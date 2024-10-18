package no.sikt.nva.nvi.index.aws;

import static java.util.Objects.requireNonNull;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.query.SearchAggregation.APPROVED_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.APPROVED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ASSIGNMENTS_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.COMPLETED_AGGREGATION_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.DISPUTED_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.NEW_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.NEW_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
import static no.sikt.nva.nvi.index.query.SearchAggregation.PENDING_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.PENDING_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.REJECTED_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.REJECTED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.query.SearchAggregation.TOTAL_COUNT_AGGREGATION_AGG;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.randomNviContributor;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.randomPages;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.randomPublicationChannel;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.PublicationDate;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.OrderByFields;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
import no.sikt.nva.nvi.index.query.CandidateQuery.QueryFilterType;
import no.sikt.nva.nvi.index.query.SearchAggregation;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
import org.apache.http.HttpHost;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.RestClient;
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
import org.opensearch.testcontainers.OpensearchContainer;

public class OpenSearchClientTest {

    public static final int DELAY_ON_INDEX = 2000;
    public static final String YEAR = "2023";
    public static final String CATEGORY = "AcademicArticle";
    public static final String UNEXISTING_FILTER = "unexisting-filter";
    public static final URI NTNU_INSTITUTION_ID
        = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
    public static final String NTNU_INSTITUTION_IDENTIFIER = "194.0.0.0";
    public static final URI SIKT_INSTITUTION_ID
        = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    public static final String SIKT_INSTITUTION_IDENTIFIER = "20754.0.0.0";
    public static final int SCALE = 4;
    public static final String SIKT_LEVEL_2_ID = "https://api.dev.nva.aws.unit.no/cristin/organization/20754.1.0.0";
    public static final String SIKT_LEVEL_3_ID = "https://api.dev.nva.aws.unit.no/cristin/organization/20754.1.1.0";
    private static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.0.0";
    private static final URI ORGANIZATION = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    private static final String USERNAME = "user1";
    private static final OpensearchContainer container = new OpensearchContainer(OPEN_SEARCH_IMAGE);
    private static RestClient restClient;
    private static OpenSearchClient openSearchClient;

    public static void setUpTestContainer() {
        container.start();
        restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
    }

    @BeforeAll
    public static void init() {
        setUpTestContainer();
        openSearchClient = new OpenSearchClient(restClient, FakeCachedJwtProvider.setup());
    }

    @BeforeEach
    public void beforeEach() {
        openSearchClient.createIndex();
    }

    @AfterAll
    static void afterAll() {
        container.stop();
    }

    @AfterEach
    void afterEach() throws IOException {
        try {
            openSearchClient.deleteIndex();
        } catch (OpenSearchException e) {
            // ignore
        }
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

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void shouldOrderResult(String sortOrder) throws InterruptedException, IOException {
        var createdFirst = documentWithCreatedDate(Instant.now());
        var createdSecond = documentWithCreatedDate(Instant.now().plus(1, ChronoUnit.MINUTES));
        addDocumentsToIndex(createdFirst, createdSecond);
        var searchParameters =
            defaultSearchParameters().withSearchResultParameters(SearchResultParameters.builder()
                                                                     .withSortOrder(sortOrder)
                                                                     .withOrderBy(OrderByFields.CREATED_DATE.getValue())
                                                                     .build()).build();
        var searchResponse = openSearchClient.search(searchParameters);
        var hits = searchResponse.hits().hits();
        var expectedFirst = sortOrder.equals("asc") ? createdFirst.createdDate() : createdSecond.createdDate();
        var expectedSecond = sortOrder.equals("asc") ? createdSecond.createdDate() : createdFirst.createdDate();
        assertThat(requireNonNull(hits.get(0).source()).createdDate(), is(equalTo(expectedFirst)));
        assertThat(requireNonNull(hits.get(1).source()).createdDate(), is(equalTo(expectedSecond)));
    }

    @Test
    void shouldDeleteIndexAndThrowExceptionWhenSearchingInNonExistentIndex() throws IOException, InterruptedException {
        var document = singleNviCandidateIndexDocument().build();
        addDocumentsToIndex(document);
        openSearchClient.deleteIndex();
        var searchParameters = defaultSearchParameters().build();
        assertThrows(OpenSearchException.class,
                     () -> openSearchClient.search(searchParameters));
    }

    @Test
    void shouldThrowWhenUsingUndefinedFilterName() {
        var searchParameters = defaultSearchParameters().withFilter(UNEXISTING_FILTER).build();
        assertThrows(IllegalStateException.class,
                     () -> openSearchClient.search(searchParameters));
    }

    @Test
    void shouldRemoveDocumentFromIndex() throws InterruptedException, IOException {
        var document = singleNviCandidateIndexDocument().build();
        addDocumentsToIndex(document);
        openSearchClient.removeDocumentFromIndex(document.identifier());
        Thread.sleep(DELAY_ON_INDEX);
        var searchParameters = defaultSearchParameters().build();
        var searchResponse =
            openSearchClient.search(searchParameters);
        var nviCandidateIndexDocument = searchResponse.hits().hits();

        assertThat(nviCandidateIndexDocument, hasSize(0));
    }

    @ParameterizedTest
    @MethodSource("aggregationNameAndExpectedCountProvider")
    void shouldReturnAggregationsWithExpectedCount(Entry<String, Integer> entry)
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_new.json"),
                            documentFromString("document_new_collaboration.json"),
                            documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration_pending.json"),
                            documentFromString("document_approved_collaboration_new.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration_pending.json"),
                            documentFromString("document_rejected_collaboration_new.json"),
                            documentFromString("document_organization_aggregation_dispute.json"));

        var searchParameters = defaultSearchParameters().build();
        var searchResponse = openSearchClient.search(searchParameters);
        var aggregation = searchResponse.aggregations().get(entry.getKey());
        assertThat(getDocCount(aggregation), is(equalTo(entry.getValue())));
    }

    @Test
    void shouldReturnDefaultAggregationsWhenAggregationTypeAll() throws IOException {
        var searchParameters = CandidateSearchParameters.builder()
                                   .withAggregationType("all")
                                   .build();
        var searchResponse = openSearchClient.search(searchParameters);
        var aggregations = searchResponse.aggregations();
        var expectedAggregations = Arrays.stream(SearchAggregation.values())
                                       .filter(
                                           aggregation -> !ORGANIZATION_APPROVAL_STATUS_AGGREGATION.equals(aggregation))
                                       .toList();
        assertEquals(expectedAggregations.size(), aggregations.keySet().size());
    }

    @Test
    void shouldReturnSpecificAggregationsWhenSpecificAggregationTypeRequested() throws IOException {
        var requestedAggregation = randomElement(SearchAggregation.values()).getAggregationName();
        var searchParameters = CandidateSearchParameters.builder()
                                   .withAggregationType(requestedAggregation)
                                   .build();
        var searchResponse = openSearchClient.search(searchParameters);
        var aggregations = searchResponse.aggregations();
        assertEquals(1, aggregations.keySet().size());
        assertEquals(requestedAggregation, aggregations.keySet().iterator().next());
    }

    @Test
    void shouldReturnSearchResultsWithContributorAffiliatedWithSubUnitOfSearchedInstitution()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_with_contributor_from_ntnu_subunit.json"),
                            documentFromString("document_with_contributor_from_sikt.json"),
                            documentFromString("document_with_contributor_from_sikt_but_not_creator.json")
        );

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(SIKT_INSTITUTION_IDENTIFIER)).build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingSubUnit()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_with_contributor_from_ntnu_subunit.json")
        );

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER)).build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingTopLevelInstititution()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_with_contributor_from_ntnu_subunit.json"),
                            documentFromString("document_with_contributor_from_ntnu_toplevel.json"),
                            documentFromString("document_with_contributor_from_sikt.json"),
                            documentFromString("document_with_contributor_from_sikt_but_not_creator.json")
        );

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER)).build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(2));
    }

    @Test
    void shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingTopLevelInstitutionExcludingSubUnit()
        throws IOException, InterruptedException {
        var subUnitDoc = documentFromString("document_with_contributor_from_ntnu_subunit.json");
        var topLevelDoc = documentFromString("document_with_contributor_from_ntnu_toplevel.json");

        addDocumentsToIndex(subUnitDoc, topLevelDoc);

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
                .withExcludeSubUnits(true)
                .build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @ParameterizedTest
    @MethodSource("filterNameProvider")
    void shouldReturnSearchResultsUsingFilter(Entry<String, Integer> entry)
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_new.json"),
                            documentFromString("document_new_collaboration.json"),
                            documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration_pending.json"),
                            documentFromString("document_approved_collaboration_new.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration_pending.json"),
                            documentFromString("document_rejected_collaboration_new.json"),
                            documentFromString("document_organization_aggregation_dispute.json"));

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
                .withFilter(entry.getKey())
                .build();

        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(entry.getValue()));
    }

    @Test
    void shouldNotIncludeDisputesForOtherOrganizationsInDisputeFilter()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_organization_aggregation_dispute.json"),
                            documentFromString("document_dispute_not_sikt.json"));

        var searchParameters = defaultSearchParameters().withFilter(QueryFilterType.DISPUTED_AGG.getFilter()).build();

        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(1));
        assertTrue(requireNonNull(searchResponse.hits()
                                      .hits()
                                      .get(0)
                                      .source())
                       .approvals()
                       .stream()
                       .anyMatch(approval -> approval.institutionId().equals(searchParameters.topLevelCristinOrg())));
    }

    @Test
    void shouldReturnHitOnSearchTermPublicationIdentifier() throws IOException, InterruptedException {
        var indexDocuments = generateNumberOfCandidates(5);
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var searchTerm = indexDocuments.get(2).publicationDetails().identifier();
        var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(1));
        assertEquals(searchTerm, getFirstHit(searchResponse).publicationDetails().identifier());
    }

    @Test
    void shouldReturnHitOnSearchTermCandidateIdentifier() throws IOException, InterruptedException {
        var indexDocuments = generateNumberOfCandidates(5);
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var searchTerm = indexDocuments.get(2).identifier().toString();
        var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(1));
        assertEquals(searchTerm, getFirstHit(searchResponse).identifier().toString());
    }

    @Test
    void shouldReturnHitOnSearchTermPublicationTitle() throws IOException, InterruptedException {
        var indexDocuments = generateNumberOfCandidates(5);
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var searchTerm = indexDocuments.get(2).publicationDetails().title();
        var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(1));
        assertEquals(searchTerm, getFirstHit(searchResponse).publicationDetails().title());
    }

    @Test
    void shouldReturnHitsOnSearchTermsPartOfPublicationTitle() throws IOException, InterruptedException {
        var firstTitle = "Start of sentence. Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        var secondTitle = "Another hit. First word lorem ipsum dolor sit amet, something else";
        var thirdTitleShouldNotGetHit = "Some other title";
        var indexDocuments = List.of(indexDocumentWithTitle(firstTitle), indexDocumentWithTitle(secondTitle),
                                     indexDocumentWithTitle(thirdTitleShouldNotGetHit));
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var searchTerm = "lorem ipsum";
        var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(2));
        assertThat(searchResponse.hits().hits().stream().map(hit -> hit.source().publicationDetails().title()).toList(),
                   containsInAnyOrder(firstTitle, secondTitle));
    }

    @Test
    void shouldReturnHitOnSearchTermContributorName() throws IOException, InterruptedException {
        var indexDocuments = generateNumberOfCandidates(5);
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var expectedHit = indexDocuments.get(2);
        var searchTerm = expectedHit.publicationDetails().contributors().get(0).name();
        var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(1));
        assertEquals(expectedHit.identifier(), getFirstHit(searchResponse).identifier());
    }

    @Test
    void shouldReturnHitOnSearchTermPublicationAbstract() throws IOException, InterruptedException {
        var indexDocuments = generateNumberOfCandidates(5);
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var searchTerm = indexDocuments.get(2).publicationDetails().abstractText();
        var searchParameters = defaultSearchParameters().withSearchTerm(searchTerm).build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(1));
        assertEquals(searchTerm, getFirstHit(searchResponse).publicationDetails().abstractText());
    }

    @Test
    void shouldReturnAllWhenSearchTermNotProvided() throws InterruptedException, IOException {
        var indexDocuments = generateNumberOfCandidates(5);
        addDocumentsToIndex(indexDocuments.toArray(new NviCandidateIndexDocument[0]));
        var searchParameters = defaultSearchParameters().build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(5));
    }

    @Test
    void shouldReturnSingleDocumentWhenFilteringByYear() throws InterruptedException, IOException {
        var customer = randomUri();
        var year = randomString();
        var document = singleNviCandidateIndexDocumentWithCustomer(customer, randomString(),
                                                                   randomString(), year,
                                                                   randomString());
        addDocumentsToIndex(document,
                            singleNviCandidateIndexDocumentWithCustomer(customer, randomString(),
                                                                        randomString(), randomString(),
                                                                        randomString()));

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(getLastPathElement(customer))).withYear(year).build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldReturnSingleDocumentWhenFilteringByTitle() throws InterruptedException, IOException {
        var customer = randomUri();
        var title = randomString().concat(" ").concat(randomString()).concat(" ").concat(randomString());
        var document = singleNviCandidateIndexDocumentWithCustomer(customer, randomString(),
                                                                   randomString(), YEAR, title);
        addDocumentsToIndex(document,
                            singleNviCandidateIndexDocumentWithCustomer(customer, randomString(),
                                                                        randomString(), randomString(),
                                                                        randomString()));

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(getLastPathElement(customer)))
                .withTitle(getRandomWord(title))
                .withYear(YEAR)
                .build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldReturnSingleDocumentWhenFilteringByContributor() throws InterruptedException, IOException {
        var customer = randomUri();
        var contributor = randomString().concat(" ").concat(randomString()).concat(" ").concat(randomString());
        var document = singleNviCandidateIndexDocumentWithCustomer(customer,
                                                                   contributor, randomString(),
                                                                   YEAR, randomString());
        addDocumentsToIndex(document, singleNviCandidateIndexDocumentWithCustomer(
            customer, randomString(), randomString(), randomString(), randomString()));

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(getLastPathElement(customer)))
                .withContributor(getRandomWord(contributor))
                .withYear(YEAR)
                .build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldReturnSingleDocumentWhenFilteringByAssignee() throws InterruptedException, IOException {
        var customer = randomUri();
        var assignee = randomString().concat(" ").concat(randomString()).concat(" ").concat(randomString());
        var document = singleNviCandidateIndexDocumentWithCustomer(customer, randomString(), assignee,
                                                                   YEAR, randomString());
        addDocumentsToIndex(document, singleNviCandidateIndexDocumentWithCustomer(
            customer, randomString(), randomString(), randomString(), randomString()));

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(getLastPathElement(customer)))
                .withAssignee(getRandomWord(assignee))
                .withYear(YEAR)
                .build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @ParameterizedTest
    @MethodSource("filterNameProvider")
    void shouldReturnSearchResultsUsingFilterAndSearchTermCombined(Entry<String, Integer> entry)
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_new.json"),
                            documentFromString("document_new_collaboration.json"),
                            documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration_pending.json"),
                            documentFromString("document_approved_collaboration_new.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration_pending.json"),
                            documentFromString("document_rejected_collaboration_new.json"),
                            documentFromString("document_organization_aggregation_dispute.json"));

        var searchParameters =
            defaultSearchParameters().withFilter(entry.getKey())
                .withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
                .build();
        var searchResponse = openSearchClient.search(searchParameters);
        assertThat(searchResponse.hits().hits(), hasSize(entry.getValue()));
    }

    @Test
    void shouldReturnSingleDocumentWhenFilteringByCategory() throws InterruptedException, IOException {
        addDocumentsToIndex(documentFromString("document_new.json"),
                            documentFromString("document_pending_category_degree_bachelor.json"));

        var searchParameters =
            defaultSearchParameters().withCategory(CATEGORY)
                .withAffiliations(List.of(NTNU_INSTITUTION_IDENTIFIER))
                .build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldNotThrowExceptionWhenSearchingWithFilterWithoutInstitution() {
        var searchParameters = CandidateSearchParameters.builder()
                                   .withUsername(randomString())
                                   .withFilter("pending")
                                   .build();
        assertDoesNotThrow(() -> openSearchClient.search(searchParameters));
    }

    @Test
    void shouldReturnAllSearchResultsWhenSearchingWithoutCustomerAndAffiliations()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_with_contributor_from_ntnu_subunit.json"),
                            documentFromString("document_with_contributor_from_sikt.json"),
                            documentFromString("document_with_contributor_from_sikt_but_not_creator.json")
        );

        var searchParameters = CandidateSearchParameters.builder().build();
        var searchResponse = openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(3));
    }

    @Test
    void shouldReturnOrganizationAggregationWithSubAggregations() throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_organization_aggregation_pending.json"));
        addDocumentsToIndex(documentFromString("document_organization_aggregation_new.json"));
        addDocumentsToIndex(documentFromString("document_organization_aggregation_dispute.json"));
        var aggregation = SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
        var searchParameters = defaultSearchParameters().withAggregationType(aggregation).build();
        var searchResponse = openSearchClient.search(searchParameters);
        var actualAggregate = searchResponse.aggregations().get(aggregation);
        var actualOrganizationAggregation = ((NestedAggregate) actualAggregate._get()).aggregations()
                                                .get(SIKT_INSTITUTION_ID.toString());
        var filterAggregate = ((FilterAggregate) actualOrganizationAggregation._get()).aggregations()
                                  .get("organizations");
        var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
        assertExpectedOrganizationAggregations(actualOrgBuckets);
    }

    @Test
    void shouldReturnOrganizationAggregationWithSubAggregationsForUpToOneThousandInvolvedOrgs()
        throws IOException, InterruptedException {
        addDocumentsToIndex(nviCandidateWithOneThousandInvolvedOrgs());
        var aggregation = SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
        var searchParameters = defaultSearchParameters().withAggregationType(aggregation).build();
        var searchResponse = openSearchClient.search(searchParameters);
        var actualAggregate = searchResponse.aggregations().get(aggregation);
        var actualOrganizationAggregation = ((NestedAggregate) actualAggregate._get()).aggregations()
                                                .get(SIKT_INSTITUTION_ID.toString());
        var filterAggregate = ((FilterAggregate) actualOrganizationAggregation._get()).aggregations()
                                  .get("organizations");
        var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
        assertEquals(1000, actualOrgBuckets.array().size());
    }

    @Test
    void organizationAggregationShouldNotContainAggregationsForOtherTopLevelOrgs()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_organization_aggregation_collaboration.json"));
        var aggregation = SearchAggregation.ORGANIZATION_APPROVAL_STATUS_AGGREGATION.getAggregationName();
        var searchParameters =
            defaultSearchParameters().withTopLevelCristinOrg(SIKT_INSTITUTION_ID)
                .withAggregationType(aggregation)
                .build();
        var searchResponse = openSearchClient.search(searchParameters);
        var actualAggregate = searchResponse.aggregations().get(aggregation);
        var actualOrganizationAggregation = ((NestedAggregate) actualAggregate._get()).aggregations()
                                                .get(SIKT_INSTITUTION_ID.toString());
        var filterAggregate = ((FilterAggregate) actualOrganizationAggregation._get()).aggregations()
                                  .get("organizations");
        var actualOrgBuckets = ((StringTermsAggregate) filterAggregate._get()).buckets();
        var orgIds = actualOrgBuckets.array().stream().map(StringTermsBucket::key).toList();
        assertThat(orgIds, containsInAnyOrder(SIKT_INSTITUTION_ID.toString()));
        assertThat(orgIds, not(containsInAnyOrder(NTNU_INSTITUTION_ID.toString())));
    }

    @Test
    void shouldExcludeFields() throws IOException, InterruptedException {
        var document = documentWithContributors();
        addDocumentsToIndex(document);
        var searchParameters = defaultSearchParameters()
                                   .withExcludeFields(List.of("publicationDetails.contributors"))
                                   .build();
        var searchResponse = openSearchClient.search(searchParameters);
        var firstHit = getFirstHit(searchResponse);
        assertNull(requireNonNull(firstHit).publicationDetails().contributors());
    }

    private static NviCandidateIndexDocument nviCandidateWithOneThousandInvolvedOrgs() {
        return singleNviCandidateIndexDocument().withApprovals(List.of(
            Approval.builder()
                .withInstitutionId(URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0"))
                .withApprovalStatus(ApprovalStatus.NEW)
                .withInvolvedOrganizations(IntStream.range(0, 1000)
                                               .mapToObj(i -> URI.create(
                                                   "https://api.dev.nva.aws.unit.no/cristin/organization/"
                                                   + i + ".0.0.0"))
                                               .collect(Collectors.toSet()))
                .build())).build();
    }

    private static NviCandidateIndexDocument getFirstHit(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().get(0).source();
    }

    private static List<NviCandidateIndexDocument> generateNumberOfCandidates(int number) {
        return IntStream.range(0, number)
                   .mapToObj(i -> singleNviCandidateIndexDocument().build())
                   .toList();
    }

    private static NviCandidateIndexDocument documentWithContributors() {
        return singleNviCandidateIndexDocument()
                   .withPublicationDetails(publicationDetailsBuilder()
                                               .withContributors(List.of(randomNviContributor(randomUri()))).build())
                   .build();
    }

    private static String getLastPathElement(URI customer) {
        return UriWrapper.fromUri(customer).getLastPathElement();
    }

    private static void assertExpectedOrganizationAggregations(
        Buckets<StringTermsBucket> actualStatusBuckets) {
        actualStatusBuckets.array().forEach(bucket -> {
            assertExpectedStatusAggregations(bucket);
            assertExpectedPointAggregations(bucket);
            assertExpectedDisputeAggregations(bucket);
        });
    }

    private static void assertExpectedPointAggregations(StringTermsBucket bucket) {
        var key = bucket.key();
        var pointAggregation = (SumAggregate) bucket.aggregations().get("points")._get();
        if (key.equals(SIKT_INSTITUTION_ID.toString())) {
            assertEquals(4.0, pointAggregation.value());
        } else if (key.equals(SIKT_LEVEL_2_ID)) {
            assertEquals(4.0, pointAggregation.value());
        } else if (key.equals(SIKT_LEVEL_3_ID)) {
            assertEquals(3.0, pointAggregation.value());
        } else {
            throw new RuntimeException("Unexpected key: " + key);
        }
    }

    private static void assertExpectedDisputeAggregations(StringTermsBucket bucket) {
        var disputeAggregation = (FilterAggregate) bucket.aggregations().get("dispute")._get();
        var key = bucket.key();
        if (key.equals(SIKT_INSTITUTION_ID.toString()) || key.equals(SIKT_LEVEL_2_ID)) {
            assertEquals(1, disputeAggregation.docCount());
        } else if (key.equals(SIKT_LEVEL_3_ID)) {
            assertEquals(0, disputeAggregation.docCount());
        } else {
            throw new RuntimeException("Unexpected key: " + key);
        }
    }

    private static void assertExpectedStatusAggregations(StringTermsBucket bucket) {
        var key = bucket.key();
        var statusAggregation = bucket.aggregations().get("status");
        if (key.equals(SIKT_INSTITUTION_ID.toString())) {
            var expectedKeys = List.of(NEW.getValue(), PENDING.getValue(), REJECTED.getValue());
            assertExpectedSubAggregations(statusAggregation, expectedKeys);
        } else if (key.equals(SIKT_LEVEL_2_ID)) {
            var expectedKeys = List.of(NEW.getValue(), PENDING.getValue(), REJECTED.getValue());
            assertExpectedSubAggregations(statusAggregation, expectedKeys);
        } else if (key.equals(SIKT_LEVEL_3_ID)) {
            var expectedKeys = List.of(PENDING.getValue());
            assertExpectedSubAggregations(statusAggregation, expectedKeys);
        } else {
            throw new RuntimeException("Unexpected key: " + key);
        }
    }

    private static void assertExpectedSubAggregations(Aggregate subAggregation, List<String> expectedKeys) {
        assertEquals(Kind.Sterms, subAggregation._kind());
        var subBuckets = ((StringTermsAggregate) subAggregation._get()).buckets();
        assertEquals(expectedKeys.size(), subBuckets.array().size());
        assertContainsKeys(expectedKeys, subBuckets);
    }

    private static void assertContainsKeys(List<String> expectedKeys, Buckets<StringTermsBucket> subBuckets) {
        expectedKeys.forEach(key -> assertContainsKey(subBuckets, key));
    }

    private static void assertContainsKey(Buckets<StringTermsBucket> subBuckets, String orgId) {
        assertThat(
            subBuckets.array().stream().filter(subBucket -> subBucket.key().equals(orgId)).count(), is(1L));
    }

    private static void addDocumentToIndex() {
        try {
            addDocumentsToIndex(singleNviCandidateIndexDocumentWithCustomer(ORGANIZATION, randomString(),
                                                                            randomString(), YEAR, randomString()));
        } catch (InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    @NotNull
    private static SearchResultParameters getSearchResultParameters(int offset, int size) {
        return SearchResultParameters.builder().withOffset(offset).withSize(size).build();
    }

    private static int extractTotalNumberOfHits(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return (int) searchResponse.hits().total().value();
    }

    private static NviCandidateIndexDocument documentFromString(String fileName) throws JsonProcessingException {
        var string = IoUtils.stringFromResources(Path.of(fileName));
        return dtoObjectMapper.readValue(string, NviCandidateIndexDocument.class);
    }

    private static NviCandidateIndexDocument.Builder singleNviCandidateIndexDocument() {
        var approvals = randomApprovalList();
        return NviCandidateIndexDocument.builder()
                   .withIdentifier(UUID.randomUUID())
                   .withPublicationDetails(randomPublicationDetails())
                   .withApprovals(approvals)
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(randomBigDecimal())
                   .withCreatedDate(Instant.now());
    }

    private static NviCandidateIndexDocument indexDocumentWithTitle(String title) {
        var approvals = randomApprovalList();
        return NviCandidateIndexDocument.builder()
                   .withIdentifier(UUID.randomUUID())
                   .withPublicationDetails(publicationDetailsWithTitle(title))
                   .withApprovals(approvals)
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(randomBigDecimal())
                   .withCreatedDate(Instant.now())
                   .build();
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocumentWithCustomer(URI customer,
                                                                                         String contributor,
                                                                                         String assignee,
                                                                                         String year,
                                                                                         String title) {
        return NviCandidateIndexDocument.builder()
                   .withIdentifier(UUID.randomUUID())
                   .withPublicationDetails(randomPublicationDetailsWithCustomer(customer, contributor, year, title))
                   .withApprovals(List.of(randomApprovalWithCustomerAndAssignee(customer, assignee)))
                   .withNumberOfApprovals(1)
                   .withPoints(randomBigDecimal())
                   .withCreatedDate(Instant.now())
                   .withModifiedDate(Instant.now())
                   .build();
    }

    private static PublicationDetails randomPublicationDetailsWithCustomer(URI affiliation,
                                                                           String contributor,
                                                                           String year,
                                                                           String title) {
        var publicationDate = year != null
                                  ? PublicationDate.builder().withYear(year).build()
                                  : PublicationDate.builder().withYear(YEAR).build();
        var contributorBuilder = NviContributor.builder().withRole("Creator")
                                     .withAffiliations(List.of(NviOrganization.builder().withId(affiliation).build()));
        if (contributor != null) {
            contributorBuilder.withName(contributor);
        }
        return PublicationDetails.builder()
                   .withTitle(title)
                   .withPublicationDate(publicationDate)
                   .withContributors(List.of(contributorBuilder.build()))
                   .withPublicationChannel(randomPublicationChannel())
                   .withPages(randomPages())
                   .build();
    }

    private static Approval randomApprovalWithCustomerAndAssignee(URI affiliation, String assignee) {
        return new Approval(affiliation, Map.of(), randomStatus(), randomInstitutionPoints(), Set.of(), assignee,
                            randomGlobalApprovalStatus());
    }

    private static List<Approval> randomApprovalList() {
        return IntStream.range(0, 5).boxed().map(i -> randomApproval()).toList();
    }

    private static Approval randomApproval() {
        return new Approval(ORGANIZATION, Map.of(), randomStatus(), randomInstitutionPoints(), Set.of(), null,
                            randomGlobalApprovalStatus());
    }

    private static InstitutionPoints randomInstitutionPoints() {
        return new InstitutionPoints(randomUri(), randomBigDecimal(SCALE), randomCreatorAffiliationPoints());
    }

    private static List<CreatorAffiliationPoints> randomCreatorAffiliationPoints() {
        return List.of(new CreatorAffiliationPoints(randomUri(), randomUri(), randomBigDecimal(SCALE)));
    }

    private static ApprovalStatus randomStatus() {
        var values = Arrays.stream(ApprovalStatus.values()).toList();
        var size = values.size();
        var random = new Random();
        return values.get(random.nextInt(size));
    }

    private static GlobalApprovalStatus randomGlobalApprovalStatus() {
        return randomElement(GlobalApprovalStatus.values());
    }

    private static PublicationDetails randomPublicationDetails() {
        return publicationDetailsBuilder().build();
    }

    private static PublicationDetails publicationDetailsWithTitle(String title) {
        return publicationDetailsBuilder().withTitle(title).build();
    }

    private static PublicationDetails.Builder publicationDetailsBuilder() {
        return PublicationDetails.builder()
                   .withId(randomUri().toString())
                   .withTitle(randomString())
                   .withAbstract(randomString())
                   .withPublicationDate(PublicationDate.builder().withYear(YEAR).build())
                   .withPublicationChannel(randomPublicationChannel())
                   .withPages(randomPages())
                   .withContributors(List.of(randomNviContributor(randomUri())));
    }

    private static void addDocumentsToIndex(NviCandidateIndexDocument... documents) throws InterruptedException {
        Arrays.stream(documents).forEach(document -> openSearchClient.addDocumentToIndex(document));
        Thread.sleep(DELAY_ON_INDEX);
    }

    private static Stream<Entry<String, Integer>> aggregationNameAndExpectedCountProvider() {
        var map = new HashMap<String, Integer>();
        map.put(NEW_AGG.getAggregationName(), 2);
        map.put(NEW_COLLABORATION_AGG.getAggregationName(), 1);
        map.put(PENDING_AGG.getAggregationName(), 2);
        map.put(PENDING_COLLABORATION_AGG.getAggregationName(), 1);
        map.put(APPROVED_AGG.getAggregationName(), 3);
        map.put(APPROVED_COLLABORATION_AGG.getAggregationName(), 2);
        map.put(REJECTED_AGG.getAggregationName(), 3);
        map.put(REJECTED_COLLABORATION_AGG.getAggregationName(), 2);
        map.put(DISPUTED_AGG.getAggregationName(), 1);
        map.put(ASSIGNMENTS_AGG.getAggregationName(), 5);
        map.put(COMPLETED_AGGREGATION_AGG.getAggregationName(), 7);
        map.put(TOTAL_COUNT_AGGREGATION_AGG.getAggregationName(), 11);
        return map.entrySet().stream();
    }

    private static Stream<Entry<String, Integer>> filterNameProvider() {
        var map = new HashMap<String, Integer>();
        map.put(QueryFilterType.NEW_AGG.getFilter(), 2);
        map.put(QueryFilterType.NEW_COLLABORATION_AGG.getFilter(), 1);
        map.put(QueryFilterType.PENDING_AGG.getFilter(), 2);
        map.put(QueryFilterType.PENDING_COLLABORATION_AGG.getFilter(), 1);
        map.put(QueryFilterType.APPROVED_AGG.getFilter(), 3);
        map.put(QueryFilterType.APPROVED_COLLABORATION_AGG.getFilter(), 2);
        map.put(QueryFilterType.REJECTED_AGG.getFilter(), 3);
        map.put(QueryFilterType.REJECTED_COLLABORATION_AGG.getFilter(), 2);
        map.put(QueryFilterType.ASSIGNMENTS_AGG.getFilter(), 5);
        map.put(QueryFilterType.DISPUTED_AGG.getFilter(), 1);
        return map.entrySet().stream();
    }

    private static CandidateSearchParameters.Builder defaultSearchParameters() {
        return CandidateSearchParameters.builder()
                   .withAffiliations(List.of())
                   .withTopLevelCristinOrg(ORGANIZATION)
                   .withUsername(USERNAME)
                   .withYear(YEAR);
    }

    private static String getRandomWord(String str) {
        String[] words = str.split(" ");
        Random random = new Random();
        int index = random.nextInt(words.length);
        return words[index];
    }

    private int getDocCount(Aggregate aggregation) {
        if (aggregation._get() instanceof FilterAggregate filterAggregate) {
            return (int) filterAggregate.docCount();
        }
        return 0;
    }

    private NviCandidateIndexDocument documentWithCreatedDate(Instant createdDate) {
        return singleNviCandidateIndexDocument().withCreatedDate(createdDate).build();
    }

    public static final class FakeCachedJwtProvider {

        public static String TEST_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJPbmxpbmUgSldUIEJ1"
                                          + "aWxkZXIiLCJpYXQiOjE2Njg1MTE4NTcsImV4cCI6MTcwMDA0Nzg1NywiYXVkIjoi"
                                          + "d3d3LmV4YW1wbGUuY29tIiwic3ViIjoianJvY2tldEBleGFtcGxlLmNvbSIsIkdpd"
                                          + "mVuTmFtZSI6IkpvaG5ueSIsIlN1cm5hbWUiOiJSb2NrZXQiLCJFbWFpbCI6Impyb2"
                                          + "NrZXRAZXhhbXBsZS5jb20iLCJSb2xlIjoiTWFuYWdlciIsInNjb3BlIjoiZXhhbX"
                                          + "BsZS1zY29wZSJ9.ne8Jb4f2xao1zSJFZxIBRrh4WFNjkaBRV3-Ybp6fHZU";

        public static CachedJwtProvider setup() {
            var jwt = mock(DecodedJWT.class);
            var cognitoAuthenticatorMock = mock(CognitoAuthenticator.class);

            when(jwt.getToken()).thenReturn(TEST_TOKEN);
            when(jwt.getExpiresAt()).thenReturn(Date.from(Instant.now().plus(Duration.ofMinutes(5))));
            when(cognitoAuthenticatorMock.fetchBearerToken()).thenReturn(jwt);

            return new CachedJwtProvider(cognitoAuthenticatorMock, Clock.systemDefaultZone());
        }
    }
}
