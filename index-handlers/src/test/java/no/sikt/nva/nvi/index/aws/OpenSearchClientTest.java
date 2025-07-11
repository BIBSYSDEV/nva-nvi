package no.sikt.nva.nvi.index.aws;

import static java.util.Objects.requireNonNull;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomPublicationDetailsBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomNviContributor;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomNviContributorBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPages;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPublicationChannel;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.query.Aggregations.APPROVAL_ORGANIZATIONS_AGGREGATION;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.OrderByFields;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
import no.sikt.nva.nvi.index.query.QueryFilterType;
import no.sikt.nva.nvi.index.query.SearchAggregation;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregate.Kind;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.SumAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;

// These are not IP addresses, but cristin org identifier examples
// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.GodClass")
class OpenSearchClientTest {

  public static final String YEAR = String.valueOf(CURRENT_YEAR);
  public static final String CATEGORY = "AcademicArticle";
  public static final String UNEXISTING_FILTER = "unexisting-filter";
  public static final URI NTNU_INSTITUTION_ID =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
  public static final String NTNU_INSTITUTION_IDENTIFIER = "194.0.0.0";
  public static final URI SIKT_INSTITUTION_ID =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  public static final String SIKT_INSTITUTION_IDENTIFIER = "20754.0.0.0";
  public static final String SIKT_LEVEL_2_ID =
      "https://api.dev.nva.aws.unit.no/cristin/organization/20754.1.0.0";
  public static final String SIKT_LEVEL_3_ID =
      "https://api.dev.nva.aws.unit.no/cristin/organization/20754.1.1.0";
  private static final URI ORGANIZATION =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  private static final String USERNAME = "user1";
  private static final String DOCUMENT_NEW_JSON = "document_new.json";
  private static final String DOCUMENT_ORGANIZATION_AGGREGATION_DISPUTE_JSON =
      "document_organization_aggregation_dispute.json";
  private static final String DOCUMENT_WITH_CONTRIBUTOR_FROM_NTNU_SUBUNIT_JSON =
      "document_with_contributor_from_ntnu_subunit.json";
  private static final String UNEXPECTED_KEY = "Unexpected key: ";
  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static OpenSearchClient openSearchClient;

  @BeforeAll
  static void beforeAll() {
    CONTAINER.start();
    openSearchClient = CONTAINER.getOpenSearchClient();
  }

  @AfterAll
  static void afterAll() {
    CONTAINER.stop();
  }

  @BeforeEach
  void beforeEach() {
    CONTAINER.createIndex();
  }

  @AfterEach
  void afterEach() {
    CONTAINER.deleteIndex();
  }

  @Test
  void shouldReturnDocumentsFromIndexAccordingToGivenOffsetAndSize() throws IOException {
    int totalNumberOfDocuments = 4;
    IntStream.range(0, totalNumberOfDocuments).forEach(i -> addDocumentToIndex());
    int offset = 2;
    int size = 2;
    var searchParameters =
        CandidateSearchParameters.builder()
            .withUsername(USERNAME)
            .withAffiliations(List.of(getLastPathElement(ORGANIZATION)))
            .withTopLevelCristinOrg(ORGANIZATION)
            .withYear(YEAR)
            .withSearchResultParameters(getSearchResultParameters(offset, size))
            .build();
    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(extractTotalNumberOfHits(searchResponse), is(equalTo(totalNumberOfDocuments)));

    int expectedNumberOfHitsReturned = totalNumberOfDocuments - offset;
    assertThat(searchResponse.hits().hits().size(), is(equalTo(expectedNumberOfHitsReturned)));
  }

  @ParameterizedTest(name = "shouldOrderResult {0}")
  @ValueSource(strings = {"asc", "desc"})
  void shouldOrderResult(String sortOrder) throws IOException {
    var createdFirst = documentWithCreatedDate(Instant.now());
    var createdSecond = documentWithCreatedDate(Instant.now().plus(1, ChronoUnit.MINUTES));
    addDocumentsToIndex(createdFirst, createdSecond);
    var searchParameters =
        defaultSearchParameters()
            .withSearchResultParameters(
                SearchResultParameters.builder()
                    .withSortOrder(sortOrder)
                    .withOrderBy(OrderByFields.CREATED_DATE.getValue())
                    .build())
            .build();
    var searchResponse = openSearchClient.search(searchParameters);
    var hits = searchResponse.hits().hits();
    var expectedFirst =
        "asc".equals(sortOrder) ? createdFirst.createdDate() : createdSecond.createdDate();
    var expectedSecond =
        "asc".equals(sortOrder) ? createdSecond.createdDate() : createdFirst.createdDate();
    assertThat(requireNonNull(hits.get(0).source()).createdDate(), is(equalTo(expectedFirst)));
    assertThat(requireNonNull(hits.get(1).source()).createdDate(), is(equalTo(expectedSecond)));
  }

  @Test
  void shouldDeleteIndexAndThrowExceptionWhenSearchingInNonExistentIndex() throws IOException {
    var document = randomIndexDocumentBuilder().build();
    addDocumentsToIndex(document);
    openSearchClient.deleteIndex();
    var searchParameters = defaultSearchParameters().build();
    assertThrows(OpenSearchException.class, () -> openSearchClient.search(searchParameters));
  }

  @Test
  void shouldThrowWhenUsingUndefinedFilterName() {
    var searchParameters = defaultSearchParameters().withFilter(UNEXISTING_FILTER).build();
    assertThrows(IllegalArgumentException.class, () -> openSearchClient.search(searchParameters));
  }

  @Test
  void shouldRemoveDocumentFromIndex() throws IOException {
    var document = randomIndexDocumentBuilder().build();
    addDocumentsToIndex(document);
    openSearchClient.removeDocumentFromIndex(document.identifier());
    CONTAINER.refreshIndex();
    var searchParameters = defaultSearchParameters().build();
    var searchResponse = openSearchClient.search(searchParameters);
    var nviCandidateIndexDocument = searchResponse.hits().hits();

    assertThat(nviCandidateIndexDocument, hasSize(0));
  }

  @Test
  void shouldReturnDefaultAggregationsWhenAggregationTypeAll() throws IOException {
    var searchParameters = CandidateSearchParameters.builder().withAggregationType("all").build();
    var searchResponse = openSearchClient.search(searchParameters);
    var aggregations = searchResponse.aggregations();
    var expectedAggregations =
        Arrays.stream(SearchAggregation.values())
            .filter(aggregation -> !ORGANIZATION_APPROVAL_STATUS_AGGREGATION.equals(aggregation))
            .toList();
    assertEquals(expectedAggregations.size(), aggregations.size());
  }

  @Test
  void shouldReturnSpecificAggregationsWhenSpecificAggregationTypeRequested() throws IOException {
    var requestedAggregation = randomElement(SearchAggregation.values()).getAggregationName();
    var searchParameters =
        CandidateSearchParameters.builder().withAggregationType(requestedAggregation).build();
    var searchResponse = openSearchClient.search(searchParameters);
    var aggregations = searchResponse.aggregations();
    assertEquals(1, aggregations.size());
    assertEquals(requestedAggregation, aggregations.keySet().iterator().next());
  }

  @Test
  void shouldReturnSearchResultsWithContributorAffiliatedWithSubUnitOfSearchedInstitution()
      throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_WITH_CONTRIBUTOR_FROM_NTNU_SUBUNIT_JSON),
        documentFromString("document_with_contributor_from_sikt.json"));

    var searchParameters =
        defaultSearchParameters().withAffiliations(List.of(SIKT_INSTITUTION_IDENTIFIER)).build();
    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(1));
  }

  @Test
  void shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingSubUnit()
      throws IOException {
    addDocumentsToIndex(documentFromString(DOCUMENT_WITH_CONTRIBUTOR_FROM_NTNU_SUBUNIT_JSON));

    var searchParameters =
        defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER)).build();
    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(1));
  }

  @Test
  void
      shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingTopLevelInstitution()
          throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_WITH_CONTRIBUTOR_FROM_NTNU_SUBUNIT_JSON),
        documentFromString("document_with_contributor_from_ntnu_toplevel.json"),
        documentFromString("document_with_contributor_from_sikt.json"));

    var searchParameters =
        defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER)).build();
    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(2));
  }

  @Test
  void
      shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingTopLevelInstitutionExcludingSubUnit()
          throws IOException {
    var subUnitDoc = documentFromString(DOCUMENT_WITH_CONTRIBUTOR_FROM_NTNU_SUBUNIT_JSON);
    var topLevelDoc = documentFromString("document_with_contributor_from_ntnu_toplevel.json");

    addDocumentsToIndex(subUnitDoc, topLevelDoc);

    var searchParameters =
        defaultSearchParameters()
            .withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
            .withExcludeSubUnits(true)
            .build();
    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(1));
  }

  @ParameterizedTest(name = "shouldReturnSearchResultsUsingFilter {0}")
  @MethodSource("filterNameProvider")
  void shouldReturnSearchResultsUsingFilter(Entry<String, Integer> entry) throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_NEW_JSON),
        documentFromString("document_new_collaboration.json"),
        documentFromString("document_pending.json"),
        documentFromString("document_pending_collaboration.json"),
        documentFromString("document_approved.json"),
        documentFromString("document_approved_collaboration_pending.json"),
        documentFromString("document_approved_collaboration_new.json"),
        documentFromString("document_rejected.json"),
        documentFromString("document_rejected_collaboration_pending.json"),
        documentFromString("document_rejected_collaboration_new.json"),
        documentFromString(DOCUMENT_ORGANIZATION_AGGREGATION_DISPUTE_JSON));

    var searchParameters =
        defaultSearchParameters()
            .withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
            .withFilter(entry.getKey())
            .build();

    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(entry.getValue()));
  }

  @Test
  void shouldNotIncludeDisputesForOtherOrganizationsInDisputeFilter() throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_ORGANIZATION_AGGREGATION_DISPUTE_JSON),
        documentFromString("document_dispute_not_sikt.json"));

    var searchParameters =
        defaultSearchParameters().withFilter(QueryFilterType.DISPUTED_AGG.getValue()).build();

    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertTrue(
        requireNonNull(searchResponse.hits().hits().getFirst().source()).approvals().stream()
            .anyMatch(
                approval ->
                    approval.institutionId().equals(searchParameters.topLevelCristinOrg())));
  }

  @Test
  void shouldReturnHitOnSearchTermPublicationIdentifier() throws IOException {
    var indexDocuments = generateNumberOfCandidates(5);
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchTerm = indexDocuments.get(2).publicationDetails().identifier();
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertEquals(searchTerm, getFirstHit(searchResponse).publicationDetails().identifier());
  }

  @Test
  void shouldReturnHitOnSearchTermCandidateIdentifier() throws IOException {
    var indexDocuments = generateNumberOfCandidates(5);
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchTerm = indexDocuments.get(2).identifier().toString();
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertEquals(searchTerm, getFirstHit(searchResponse).identifier().toString());
  }

  @Test
  void shouldReturnHitOnSearchTermPublicationTitle() throws IOException {
    var indexDocuments = generateNumberOfCandidates(5);
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchTerm = indexDocuments.get(2).publicationDetails().title();
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertEquals(searchTerm, getFirstHit(searchResponse).publicationDetails().title());
  }

  @Test
  void shouldReturnHitsOnSearchTermsPartOfPublicationTitle() throws IOException {
    var firstTitle = "Start of sentence. Lorem ipsum dolor sit amet, consectetur adipiscing elit";
    var secondTitle = "Another hit. First word lorem ipsum dolor sit amet, something else";
    var thirdTitleShouldNotGetHit = "Some other title";
    var indexDocuments =
        List.of(
            indexDocumentWithTitle(firstTitle),
            indexDocumentWithTitle(secondTitle),
            indexDocumentWithTitle(thirdTitleShouldNotGetHit));
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchTerm = "lorem ipsum";
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(2));
    assertThat(
        searchResponse.hits().hits().stream()
            .map(hit -> hit.source().publicationDetails().title())
            .toList(),
        containsInAnyOrder(firstTitle, secondTitle));
  }

  @Test
  void shouldReturnHitOnSearchTermContributorName() throws IOException {
    var indexDocuments = generateNumberOfCandidates(5);
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var expectedHit = indexDocuments.get(2);
    var searchTerm = expectedHit.publicationDetails().contributors().getFirst().name();
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertEquals(expectedHit.identifier(), getFirstHit(searchResponse).identifier());
  }

  @Test
  void shouldReturnHitOnSearchTermPublicationAbstract() throws IOException {
    var indexDocuments = generateNumberOfCandidates(5);
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchTerm = indexDocuments.get(2).publicationDetails().abstractText();
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertEquals(searchTerm, getFirstHit(searchResponse).publicationDetails().abstractText());
  }

  @Test
  void shouldReturnAllWhenSearchTermNotProvided() throws IOException {
    var indexDocuments = generateNumberOfCandidates(5);
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchParameters = defaultSearchParameters().build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(5));
  }

  @Test
  void shouldReturnOneHitWhenSearchTermExactlyPublicationIdentifier() throws IOException {
    var expectedHit = randomIndexDocumentBuilder().build();
    var searchTerm = expectedHit.publicationDetails().identifier();
    var someTitleIncludingPartsOfSearchTerm = "Some title including " + searchTerm.split("-")[0];
    var indexDocuments =
        List.of(expectedHit, indexDocumentWithTitle(someTitleIncludingPartsOfSearchTerm));
    addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
    var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));
    assertEquals(expectedHit.identifier(), getFirstHit(searchResponse).identifier());
  }

  @Test
  void shouldReturnSingleDocumentWhenFilteringByYear() throws IOException {
    var customer = randomUri();
    var year = randomString();
    var document =
        indexDocumentWithCustomer(customer, randomString(), randomString(), year, randomString());
    addDocumentsToIndex(
        document,
        indexDocumentWithCustomer(
            customer, randomString(), randomString(), randomString(), randomString()));

    var searchParameters =
        defaultSearchParameters()
            .withAffiliations(List.of(getLastPathElement(customer)))
            .withYear(year)
            .build();

    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(1));
  }

  @Test
  void shouldReturnSingleDocumentWhenFilteringByTitle() throws IOException {
    var customer = randomUri();
    var title =
        randomString().concat(" ").concat(randomString()).concat(" ").concat(randomString());
    var document = indexDocumentWithCustomer(customer, randomString(), randomString(), YEAR, title);
    addDocumentsToIndex(
        document,
        indexDocumentWithCustomer(
            customer, randomString(), randomString(), randomString(), randomString()));

    var searchParameters =
        defaultSearchParameters()
            .withAffiliations(List.of(getLastPathElement(customer)))
            .withTitle(getRandomWord(title))
            .withYear(YEAR)
            .build();

    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(1));
  }

  @ParameterizedTest(name = "shouldReturnSearchResultsUsingFilterAndSearchTermCombined {0}")
  @MethodSource("filterNameProvider")
  void shouldReturnSearchResultsUsingFilterAndSearchTermCombined(Entry<String, Integer> entry)
      throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_NEW_JSON),
        documentFromString("document_new_collaboration.json"),
        documentFromString("document_pending.json"),
        documentFromString("document_pending_collaboration.json"),
        documentFromString("document_approved.json"),
        documentFromString("document_approved_collaboration_pending.json"),
        documentFromString("document_approved_collaboration_new.json"),
        documentFromString("document_rejected.json"),
        documentFromString("document_rejected_collaboration_pending.json"),
        documentFromString("document_rejected_collaboration_new.json"),
        documentFromString(DOCUMENT_ORGANIZATION_AGGREGATION_DISPUTE_JSON));

    var searchParameters =
        defaultSearchParameters()
            .withFilter(entry.getKey())
            .withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
            .build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(entry.getValue()));
  }

  @Test
  void shouldReturnSingleDocumentWhenFilteringByCategory() throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_NEW_JSON),
        documentFromString("document_pending_category_degree_bachelor.json"));

    var searchParameters =
        defaultSearchParameters()
            .withCategory(CATEGORY)
            .withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
            .build();

    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(1));
  }

  @Test
  void shouldNotThrowExceptionWhenSearchingWithFilterWithoutInstitution() {
    var searchParameters =
        CandidateSearchParameters.builder()
            .withUsername(randomString())
            .withFilter("pending")
            .build();
    assertDoesNotThrow(() -> openSearchClient.search(searchParameters));
  }

  @Test
  void shouldReturnAllSearchResultsWhenSearchingWithoutCustomerAndAffiliations()
      throws IOException {
    addDocumentsToIndex(
        documentFromString(DOCUMENT_WITH_CONTRIBUTOR_FROM_NTNU_SUBUNIT_JSON),
        documentFromString("document_with_contributor_from_sikt.json"));

    var searchParameters = CandidateSearchParameters.builder().build();
    var searchResponse = openSearchClient.search(searchParameters);

    assertThat(searchResponse.hits().hits(), hasSize(2));
  }

  @Test
  void shouldReturnOrganizationAggregationWithSubAggregations() throws IOException {
    addDocumentsToIndex(documentFromString("document_organization_aggregation_pending.json"));
    addDocumentsToIndex(documentFromString("document_organization_aggregation_new.json"));
    addDocumentsToIndex(documentFromString(DOCUMENT_ORGANIZATION_AGGREGATION_DISPUTE_JSON));
    var aggregation = ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
    var searchParameters = defaultSearchParameters().withAggregationType(aggregation).build();
    var searchResponse = openSearchClient.search(searchParameters);
    var actualAggregate = searchResponse.aggregations().get(aggregation);
    var actualOrganizationAggregation =
        ((NestedAggregate) actualAggregate._get())
            .aggregations()
            .get(SIKT_INSTITUTION_ID.toString());
    var filterAggregate =
        ((FilterAggregate) actualOrganizationAggregation._get())
            .aggregations()
            .get(APPROVAL_ORGANIZATIONS_AGGREGATION);
    var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
    assertExpectedOrganizationAggregations(actualOrgBuckets);
  }

  @Test
  void shouldNotIncludeRejectedPointsInOrganizationAggregationWithSubAggregations()
      throws IOException {
    addDocumentsToIndex(documentFromString("document_organization_aggregation_pending.json"));
    addDocumentsToIndex(documentFromString("document_organization_aggregation_new.json"));
    addDocumentsToIndex(documentFromString("document_organization_aggregation_rejected.json"));
    var aggregation = ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
    var searchParameters = defaultSearchParameters().withAggregationType(aggregation).build();
    var searchResponse = openSearchClient.search(searchParameters);
    var actualAggregate = searchResponse.aggregations().get(aggregation);
    var actualOrganizationAggregation =
        ((NestedAggregate) actualAggregate._get())
            .aggregations()
            .get(SIKT_INSTITUTION_ID.toString());
    var filterAggregate =
        ((FilterAggregate) actualOrganizationAggregation._get())
            .aggregations()
            .get(APPROVAL_ORGANIZATIONS_AGGREGATION);
    var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
    assertExpectedPointWithoutRejectedPoints(actualOrgBuckets);
  }

  @Test
  void shouldReturnOrganizationAggregationWithSubAggregationsForUpToOneThousandInvolvedOrgs()
      throws IOException {
    addDocumentsToIndex(nviCandidateWithOneThousandInvolvedOrgs());
    var aggregation = ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
    var searchParameters = defaultSearchParameters().withAggregationType(aggregation).build();
    var searchResponse = openSearchClient.search(searchParameters);
    var actualAggregate = searchResponse.aggregations().get(aggregation);
    var actualOrganizationAggregation =
        ((NestedAggregate) actualAggregate._get())
            .aggregations()
            .get(SIKT_INSTITUTION_ID.toString());
    var filterAggregate =
        ((FilterAggregate) actualOrganizationAggregation._get())
            .aggregations()
            .get(APPROVAL_ORGANIZATIONS_AGGREGATION);
    var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
    assertEquals(1000, actualOrgBuckets.array().size());
  }

  @Test
  void organizationAggregationShouldNotContainAggregationsForOtherTopLevelOrgs()
      throws IOException {
    addDocumentsToIndex(documentFromString("document_organization_aggregation_collaboration.json"));
    var aggregation = ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
    var searchParameters =
        defaultSearchParameters()
            .withTopLevelCristinOrg(SIKT_INSTITUTION_ID)
            .withAggregationType(aggregation)
            .build();
    var searchResponse = openSearchClient.search(searchParameters);
    var actualAggregate = searchResponse.aggregations().get(aggregation);
    var actualOrganizationAggregation =
        ((NestedAggregate) actualAggregate._get())
            .aggregations()
            .get(SIKT_INSTITUTION_ID.toString());
    var filterAggregate =
        ((FilterAggregate) actualOrganizationAggregation._get())
            .aggregations()
            .get(APPROVAL_ORGANIZATIONS_AGGREGATION);
    var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
    var orgIds = actualOrgBuckets.array().stream().map(StringTermsBucket::key).toList();
    assertThat(orgIds, containsInAnyOrder(SIKT_INSTITUTION_ID.toString()));
    assertThat(orgIds, not(containsInAnyOrder(NTNU_INSTITUTION_ID.toString())));
  }

  @Test
  void shouldExcludeFields() throws IOException {
    var document = documentWithContributors();
    addDocumentsToIndex(document);
    var searchParameters =
        defaultSearchParameters()
            .withExcludeFields(List.of("publicationDetails.contributors"))
            .build();
    var searchResponse = openSearchClient.search(searchParameters);
    var firstHit = getFirstHit(searchResponse);
    assertNull(requireNonNull(firstHit).publicationDetails().contributors());
  }

  @Test
  void shouldSearchByReportingYearWhenDocumentHasReportingPeriod() throws IOException {
    // Given an index document with mismatched publication date and reporting period
    var publicationYear = String.valueOf(CURRENT_YEAR);
    var reportedYear = String.valueOf(CURRENT_YEAR - 1);
    var expectedDocument = indexDocumentWithYear(publicationYear, reportedYear);
    addDocumentsToIndex(expectedDocument);

    // When a query is made with reporting year as the year parameter
    var searchParameters = defaultSearchParameters().withYear(reportedYear).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));

    // Then the returned document has the expected publication date and reporting period
    var actualDocument = getFirstHit(searchResponse);
    assertEquals(expectedDocument, actualDocument);
  }

  @Test
  void shouldHaveBothReportingPeriodAndPublicationDateInIndexDocument() throws IOException {
    // Given an index document with mismatched publication date and reporting period
    var publicationYear = String.valueOf(CURRENT_YEAR);
    var reportedYear = String.valueOf(CURRENT_YEAR - 1);
    var expectedDocument = indexDocumentWithYear(publicationYear, reportedYear);
    addDocumentsToIndex(expectedDocument);

    // When a query is made with reporting year as the year parameter
    var searchParameters = defaultSearchParameters().withYear(reportedYear).build();
    var searchResponse = openSearchClient.search(searchParameters);
    assertThat(searchResponse.hits().hits(), hasSize(1));

    // Then the returned document has the expected publication date and reporting period
    var actualDocument = getFirstHit(searchResponse);
    var actualReportingYear = actualDocument.reportingPeriod().year();
    var actualPublicationYear = actualDocument.publicationDetails().publicationDate().year();
    assertEquals(reportedYear, actualReportingYear);
    assertEquals(publicationYear, actualPublicationYear);
  }

  @Test
  void shouldNotReturnDocumentByPublicationDate() throws IOException {
    // Given an index document with mismatched publication date and reporting period
    var publicationYear = String.valueOf(CURRENT_YEAR);
    var reportedYear = String.valueOf(CURRENT_YEAR - 1);
    var expectedDocument = indexDocumentWithYear(publicationYear, reportedYear);
    addDocumentsToIndex(expectedDocument);

    // When a query is made with publication year as the year parameter
    var searchParameters = defaultSearchParameters().withYear(publicationYear).build();
    var searchResponse = openSearchClient.search(searchParameters);

    // Then the response does not include the document
    assertThat(searchResponse.hits().hits(), hasSize(0));
  }

  @Test
  void shouldNotFailWhenDocumentIsMissingReportingPeriod() throws IOException {
    // Given an index document with missing reporting period
    var expectedDocument = randomIndexDocumentBuilder().withReportingPeriod(null).build();
    addDocumentsToIndex(expectedDocument);

    // When a query is made without a year parameter
    // And with the publication title as the search term
    var expectedTitle = expectedDocument.publicationDetails().title();
    var searchParameters = defaultSearchParameters().withTitle(expectedTitle).build();
    var searchResponse = openSearchClient.search(searchParameters);

    // Then the response includes the document
    assertThat(searchResponse.hits().hits(), hasSize(1));
    var actualDocument = getFirstHit(searchResponse);
    assertEquals(expectedDocument, actualDocument);
  }

  @Test
  void shouldFindDocumentByPartialAuthorName() throws IOException {
    // Given an index document with an NviContributor named "John Smith"
    var name = "John Smith";
    var expectedDocument =
        indexDocumentWithCustomer(randomUri(), name, randomString(), null, randomString());
    addDocumentsToIndex(expectedDocument);

    // When a query is made with search term "smith"
    var searchParameters = defaultSearchParameters().withSearchTerm("smith").build();
    var searchResponse = openSearchClient.search(searchParameters);

    // Then the response includes the document
    assertThat(searchResponse.hits().hits(), hasSize(1));
    var actualDocument = getFirstHit(searchResponse);
    assertEquals(expectedDocument, actualDocument);
  }

  @Test
  void shouldIncludeIndexDocumentTimestamp() throws IOException {
    var testStartedAt = Instant.now();
    var expectedDocument = randomIndexDocumentBuilder().build();
    addDocumentsToIndex(expectedDocument);

    var expectedTitle = expectedDocument.publicationDetails().title();
    var searchParameters = defaultSearchParameters().withTitle(expectedTitle).build();
    var searchResponse = openSearchClient.search(searchParameters);

    var actualDocument = getFirstHit(searchResponse);
    var actualTimestamp = Instant.parse(actualDocument.indexDocumentCreatedAt());
    Assertions.assertThat(actualTimestamp)
        .isAfterOrEqualTo(testStartedAt)
        .isBeforeOrEqualTo(Instant.now());
  }

  private static void assertExpectedPointWithoutRejectedPoints(
      Buckets<StringTermsBucket> actualOrgBuckets) {
    actualOrgBuckets.array().forEach(OpenSearchClientTest::assertExpectedPointAggregations);
  }

  private static NviCandidateIndexDocument nviCandidateWithOneThousandInvolvedOrgs() {
    return randomIndexDocumentBuilder()
        .withApprovals(
            List.of(
                Approval.builder()
                    .withInstitutionId(
                        URI.create(
                            "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0"))
                    .withApprovalStatus(NEW)
                    .withInvolvedOrganizations(
                        IntStream.range(0, 1000)
                            .mapToObj(
                                i ->
                                    URI.create(
                                        "https://api.dev.nva.aws.unit.no/cristin/organization/"
                                            + i
                                            + ".0.0.0"))
                            .collect(Collectors.toSet()))
                    .build()))
        .build();
  }

  private static NviCandidateIndexDocument getFirstHit(
      SearchResponse<NviCandidateIndexDocument> searchResponse) {
    return searchResponse.hits().hits().getFirst().source();
  }

  private static List<NviCandidateIndexDocument> generateNumberOfCandidates(int number) {
    return IntStream.range(0, number).mapToObj(i -> randomIndexDocumentBuilder().build()).toList();
  }

  private static NviCandidateIndexDocument documentWithContributors() {
    return randomIndexDocumentBuilder()
        .withPublicationDetails(
            randomPublicationDetailsBuilder()
                .withContributors(List.of(randomNviContributor(randomUri())))
                .build())
        .build();
  }

  private static String getLastPathElement(URI customer) {
    return UriWrapper.fromUri(customer).getLastPathElement();
  }

  private static void assertExpectedOrganizationAggregations(
      Buckets<StringTermsBucket> actualStatusBuckets) {
    actualStatusBuckets
        .array()
        .forEach(
            bucket -> {
              assertExpectedStatusAggregations(bucket);
              assertExpectedPointAggregations(bucket);
              assertExpectedDisputeAggregations(bucket);
            });
  }

  private static void assertExpectedPointAggregations(StringTermsBucket bucket) {
    var key = bucket.key();
    var pointFilterAggregate = (FilterAggregate) bucket.aggregations().get("points")._get();
    var pointSum = (SumAggregate) pointFilterAggregate.aggregations().get("total")._get();
    if (SIKT_INSTITUTION_ID.toString().equals(key)) {
      assertEquals(4.0, pointSum.value());
    } else if (SIKT_LEVEL_2_ID.equals(key)) {
      assertEquals(4.0, pointSum.value());
    } else if (SIKT_LEVEL_3_ID.equals(key)) {
      assertEquals(3.0, pointSum.value());
    } else {
      throw new RuntimeException(UNEXPECTED_KEY + key);
    }
  }

  private static void assertExpectedDisputeAggregations(StringTermsBucket bucket) {
    var disputeAggregation = (FilterAggregate) bucket.aggregations().get("dispute")._get();
    var key = bucket.key();
    if (SIKT_INSTITUTION_ID.toString().equals(key) || SIKT_LEVEL_2_ID.equals(key)) {
      assertEquals(1, disputeAggregation.docCount());
    } else if (SIKT_LEVEL_3_ID.equals(key)) {
      assertEquals(0, disputeAggregation.docCount());
    } else {
      throw new RuntimeException(UNEXPECTED_KEY + key);
    }
  }

  private static void assertExpectedStatusAggregations(StringTermsBucket bucket) {
    var key = bucket.key();
    var statusAggregation = bucket.aggregations().get("status");
    if (SIKT_INSTITUTION_ID.toString().equals(key)) {
      var expectedKeys = List.of(NEW.getValue(), PENDING.getValue(), REJECTED.getValue());
      assertExpectedSubAggregations(statusAggregation, expectedKeys);
    } else if (SIKT_LEVEL_2_ID.equals(key)) {
      var expectedKeys = List.of(NEW.getValue(), PENDING.getValue(), REJECTED.getValue());
      assertExpectedSubAggregations(statusAggregation, expectedKeys);
    } else if (SIKT_LEVEL_3_ID.equals(key)) {
      var expectedKeys = List.of(PENDING.getValue());
      assertExpectedSubAggregations(statusAggregation, expectedKeys);
    } else {
      throw new RuntimeException(UNEXPECTED_KEY + key);
    }
  }

  private static void assertExpectedSubAggregations(
      Aggregate subAggregation, List<String> expectedKeys) {
    assertEquals(Kind.Sterms, subAggregation._kind());
    var subBuckets = ((StringTermsAggregate) subAggregation._get()).buckets();
    assertEquals(expectedKeys.size(), subBuckets.array().size());
    assertContainsKeys(expectedKeys, subBuckets);
  }

  private static void assertContainsKeys(
      List<String> expectedKeys, Buckets<StringTermsBucket> subBuckets) {
    expectedKeys.forEach(key -> assertContainsKey(subBuckets, key));
  }

  private static void assertContainsKey(Buckets<StringTermsBucket> subBuckets, String orgId) {
    assertThat(
        subBuckets.array().stream().filter(subBucket -> subBucket.key().equals(orgId)).count(),
        is(1L));
  }

  private static void addDocumentToIndex() {
    addDocumentsToIndex(
        indexDocumentWithCustomer(
            ORGANIZATION, randomString(), randomString(), YEAR, randomString()));
  }

  @NotNull
  private static SearchResultParameters getSearchResultParameters(int offset, int size) {
    return SearchResultParameters.builder().withOffset(offset).withSize(size).build();
  }

  private static int extractTotalNumberOfHits(
      SearchResponse<NviCandidateIndexDocument> searchResponse) {
    return (int) searchResponse.hits().total().value();
  }

  private static NviCandidateIndexDocument documentFromString(String fileName)
      throws JsonProcessingException {
    var string = IoUtils.stringFromResources(Path.of(fileName));
    return dtoObjectMapper.readValue(string, NviCandidateIndexDocument.class);
  }

  private static NviCandidateIndexDocument indexDocumentWithTitle(String title) {
    var publicationDetails = publicationDetailsWithTitle(title);
    return randomIndexDocumentBuilder(publicationDetails).build();
  }

  private static NviCandidateIndexDocument indexDocumentWithCustomer(
      URI customer, String contributor, String assignee, String year, String title) {
    var publicationDetails =
        randomPublicationDetailsWithCustomer(customer, contributor, year, title);
    return randomIndexDocumentBuilder(publicationDetails)
        .withApprovals(List.of(randomApproval(assignee, customer)))
        .withNumberOfApprovals(1)
        .build();
  }

  private static NviCandidateIndexDocument indexDocumentWithYear(
      String publicationYear, String reportedYear) {
    var publicationDate = new PublicationDateDto(publicationYear, null, null);
    var publicationDetails =
        randomPublicationDetailsBuilder().withPublicationDate(publicationDate).build();
    var reportingPeriod = new ReportingPeriod(reportedYear);

    return randomIndexDocumentBuilder(publicationDetails)
        .withReportingPeriod(reportingPeriod)
        .build();
  }

  private static PublicationDetails randomPublicationDetailsWithCustomer(
      URI affiliation, String contributorName, String year, String title) {
    var publicationDate =
        year != null
            ? new PublicationDateDto(year, null, null)
            : new PublicationDateDto(YEAR, null, null);
    var contributor =
        randomNviContributorBuilder(affiliation)
            .withRole("Creator")
            .withName(contributorName)
            .build();
    return PublicationDetails.builder()
        .withTitle(title)
        .withPublicationDate(publicationDate)
        .withContributors(List.of(contributor))
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages())
        .build();
  }

  private static PublicationDetails publicationDetailsWithTitle(String title) {
    return randomPublicationDetailsBuilder().withTitle(title).build();
  }

  private static void addDocumentsToIndex(NviCandidateIndexDocument... documents) {
    CONTAINER.addDocumentsToIndex(documents);
  }

  private static Stream<Entry<String, Integer>> filterNameProvider() {
    var map = new HashMap<String, Integer>();
    map.put(QueryFilterType.NEW_AGG.getValue(), 2);
    map.put(QueryFilterType.NEW_COLLABORATION_AGG.getValue(), 1);
    map.put(QueryFilterType.PENDING_AGG.getValue(), 2);
    map.put(QueryFilterType.PENDING_COLLABORATION_AGG.getValue(), 1);
    map.put(QueryFilterType.APPROVED_AGG.getValue(), 3);
    map.put(QueryFilterType.APPROVED_COLLABORATION_AGG.getValue(), 2);
    map.put(QueryFilterType.REJECTED_AGG.getValue(), 3);
    map.put(QueryFilterType.REJECTED_COLLABORATION_AGG.getValue(), 2);
    map.put(QueryFilterType.ASSIGNMENTS_AGG.getValue(), 5);
    map.put(QueryFilterType.DISPUTED_AGG.getValue(), 1);
    return map.entrySet().stream();
  }

  private static CandidateSearchParameters.Builder defaultSearchParameters() {
    return CandidateSearchParameters.builder()
        .withAffiliations(List.of())
        .withTopLevelCristinOrg(ORGANIZATION)
        .withUsername(USERNAME);
  }

  private static String getRandomWord(String str) {
    String[] words = str.split(" ");
    Random random = new Random();
    int index = random.nextInt(words.length);
    return words[index];
  }

  private NviCandidateIndexDocument documentWithCreatedDate(Instant createdDate) {
    return randomIndexDocumentBuilder().withCreatedDate(createdDate).build();
  }
}
