package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.Aggregations.COMPLETED_AGGREGATION_AGG;
import static no.sikt.nva.nvi.index.Aggregations.TOTAL_COUNT_AGGREGATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNMENTS_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PENDING_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PENDING_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REJECTED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REJECTED_COLLABORATION_AGG;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.ioutils.IoUtils;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.testcontainers.OpensearchContainer;

public class OpenSearchClientTest {

    public static final String JSON_POINTER_FILTER = "/filter#";
    public static final String JSON_POINTER_DOC_COUNT = "/doc_count";
    private static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.0.0";
    private static final URI CUSTOMER = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    private static final String USERNAME = "user1";
    private static final OpensearchContainer container = new OpensearchContainer(OPEN_SEARCH_IMAGE);
    public static final int DELAY_ON_INDEX = 2000;
    public static final String YEAR = "2023";
    public static final String UNEXISTING_FILTER = "unexisting-filter";
    public static final URI NTNU_INSTITUTION_ID
        = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
    public static final URI SIKT_INSTITUTION_ID
        = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
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
    void shouldReturnDocumentsFromIndexAccordingToGivenOffsetAndSize() throws IOException, InterruptedException {
        addDocumentsToIndex(singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), YEAR));

        int totalNumberOfDocuments = 12;
        int offset = 10;
        int size = 10;
        var searchParameters =
            CandidateSearchParameters.builder()
                .withUsername(USERNAME).withAffiliations(List.of(CUSTOMER)).withCustomer(CUSTOMER)
                .withYear(YEAR).withOffset(offset).withSize(size).build();
        var searchResponse = openSearchClient.search(searchParameters);

        assertThat(extractTotalNumberOfHits(searchResponse), is(equalTo(totalNumberOfDocuments)));

        int expectedNumberOfHitsReturned = totalNumberOfDocuments - offset;
        assertThat(searchResponse.hits().hits().size(), is(equalTo(expectedNumberOfHitsReturned)));
    }

    @Test
    void shouldReturnEverythingIfYearFilterAndAffiliationsAreNotSet() throws IOException, InterruptedException {
        addDocumentsToIndex(singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), "1234"),
                            singleNviCandidateIndexDocumentWithCustomerAndYear(CUSTOMER.toString(), "12345"));

        int totalNumberOfDocuments = 2;
        var searchParameters = CandidateSearchParameters.builder().withAffiliations(List.of(SIKT_INSTITUTION_ID))
                                   .withCustomer(CUSTOMER).withUsername(USERNAME).build();
        var searchResponse = openSearchClient.search(searchParameters);

        assertThat(extractTotalNumberOfHits(searchResponse), is(equalTo(totalNumberOfDocuments)));
        assertThat(searchResponse.hits().hits().size(), is(equalTo(totalNumberOfDocuments)));
    }


    @Test
    void shouldDeleteIndexAndThrowExceptionWhenSearchingInNonExistentIndex() throws IOException, InterruptedException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(document);
        openSearchClient.deleteIndex();
        var searchParameters = defaultSearchParameters().build();
        assertThrows(OpenSearchException.class,
                     () -> openSearchClient.search(searchParameters));
    }

    @Test
    void shoulThrowWhenUsingUndefinedFilterName() {
        var searchParameters = defaultSearchParameters().withFilter(UNEXISTING_FILTER).build();
        assertThrows(IllegalStateException.class,
                     () -> openSearchClient.search(searchParameters));
    }

    @Test
    void shouldRemoveDocumentFromIndex() throws InterruptedException, IOException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(document);
        openSearchClient.removeDocumentFromIndex(document);
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
        addDocumentsToIndex(documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_assigned.json"),
                            documentFromString("document_assigned_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration.json"));

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_ID)).build();
        var searchResponse =
            openSearchClient.search(searchParameters);
        var docCount = getDocCount(searchResponse, entry.getKey());

        assertThat(docCount, is(equalTo(entry.getValue())));
    }

    @Test
    void shouldReturnSearchResultsWithContributorAffiliatedWithSubUnitOfSearchedInstitution()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_with_contributor_from_ntnu_subunit.json"),
                            documentFromString("document_with_contributor_from_sikt.json"),
                            documentFromString("document_with_contributor_from_sikt_but_not_creator.json")
        );


        var searchParameters = defaultSearchParameters().withAffiliations(List.of(SIKT_INSTITUTION_ID)).build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @Test
    void shouldReturnSearchResultsWithContributorOfSearchedInstitutionWhenSearchingSubUnit()
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_with_contributor_from_ntnu_subunit.json")
        );

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_ID)).build();
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

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_ID)).build();
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
            defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_ID)).withExcludeSubUnits(true).build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @ParameterizedTest
    @MethodSource("filterNameProvider")
    void shouldReturnSearchResultsUsingFilter(Entry<String, Integer> entry)
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_assigned.json"),
                            documentFromString("document_assigned_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration.json"));

        var searchParameters =
            defaultSearchParameters().withAffiliations(List.of(NTNU_INSTITUTION_ID)).withFilter(entry.getKey()).build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(entry.getValue()));
    }

    @Test
    void shouldReturnSingleDocumentWhenFilteringByYear() throws InterruptedException, IOException {
        var customer = randomUri();
        var year = randomString();
        var document = singleNviCandidateIndexDocumentWithCustomerAndYear(customer.toString(), year);
        addDocumentsToIndex(document,
                            singleNviCandidateIndexDocumentWithCustomerAndYear(customer.toString(), randomString()));

        var searchParameters = defaultSearchParameters().withAffiliations(List.of(customer)).withYear(year).build();

        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(1));
    }

    @ParameterizedTest
    @MethodSource("filterNameProvider")
    void shouldReturnSearchResultsUsingFilterAndSearchTermCombined(Entry<String, Integer> entry)
        throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_assigned.json"),
                            documentFromString("document_assigned_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration.json"));

        var searchParameters =
            defaultSearchParameters().withFilter(entry.getKey()).withAffiliations(List.of(NTNU_INSTITUTION_ID)).build();
        var searchResponse =
            openSearchClient.search(searchParameters);

        assertThat(searchResponse.hits().hits(), hasSize(entry.getValue()));
    }

    private static int getDocCount(SearchResponse<NviCandidateIndexDocument> response, String aggregationName) {
        var aggregations = extractAggregations(response);
        assert aggregations != null;
        return aggregations.at(JSON_POINTER_FILTER + aggregationName + JSON_POINTER_DOC_COUNT).asInt();
    }

    private static JsonNode extractAggregations(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        var writer = new StringWriter();
        var mapper = new JsonbJsonpMapper();

        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(searchResponse, generator);
        }

        var json = attempt(() -> JsonUtils.dtoObjectMapper.readTree(writer.toString())).orElseThrow();

        return json.get("aggregations");
    }

    private static int extractTotalNumberOfHits(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return (int) searchResponse.hits().total().value();
    }

    private static NviCandidateIndexDocument documentFromString(String fileName) throws JsonProcessingException {
        var string = IoUtils.stringFromResources(Path.of(fileName));
        return JsonUtils.dtoObjectMapper.readValue(string, NviCandidateIndexDocument.class);
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        var approvals = randomApprovalList();
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomPublicationDetails(),
                                             approvals, approvals.size(), TestUtils.randomBigDecimal());
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocumentWithCustomerAndYear(String customer,
                                                                                                String year) {
        var approval = new Approval(customer, Map.of(), randomStatus(), null);
        return new NviCandidateIndexDocument(randomUri(), randomString(),
                                             randomPublicationDetailsWithYearAndCurator(year, customer),
                                             List.of(approval), 1, TestUtils.randomBigDecimal());
    }

    private static List<Approval> randomApprovalList() {
        return IntStream.range(0, 5).boxed().map(i -> randomApproval()).toList();
    }

    private static Approval randomApproval() {
        return new Approval(randomString(), Map.of(), randomStatus(), null);
    }

    private static ApprovalStatus randomStatus() {
        var values = Arrays.stream(ApprovalStatus.values()).toList();
        var size = values.size();
        var random = new Random();
        return values.get(random.nextInt(size));
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(),
                                      PublicationDate.builder().withYear(randomString()).build(),
                                      List.of());
    }

    private static PublicationDetails randomPublicationDetailsWithYearAndCurator(String year, String affiliation) {
        return new PublicationDetails(randomString(), randomString(), randomString(),
                                      PublicationDate.builder().withYear(year).build(),
                                      List.of(new Contributor.Builder().withRole("Creator")
                                                  .withAffiliations(List.of(
                                                      new Affiliation(affiliation, List.of()))).build()));
    }

    private static void addDocumentsToIndex(NviCandidateIndexDocument... documents) throws InterruptedException {
        Arrays.stream(documents).forEach(document -> openSearchClient.addDocumentToIndex(document));
        Thread.sleep(DELAY_ON_INDEX);
    }

    private static Stream<Entry<String, Integer>> aggregationNameAndExpectedCountProvider() {
        var map = new HashMap<String, Integer>();
        map.put(PENDING_AGG, 2);
        map.put(PENDING_COLLABORATION_AGG, 1);
        map.put(ASSIGNED_AGG, 2);
        map.put(ASSIGNED_COLLABORATION_AGG, 1);
        map.put(APPROVED_AGG, 2);
        map.put(APPROVED_COLLABORATION_AGG, 1);
        map.put(REJECTED_AGG, 2);
        map.put(REJECTED_COLLABORATION_AGG, 1);
        map.put(ASSIGNMENTS_AGG, 4);
        map.put(COMPLETED_AGGREGATION_AGG, 4);
        map.put(TOTAL_COUNT_AGGREGATION_AGG, 8);
        return map.entrySet().stream();
    }

    private static Stream<Entry<String, Integer>> filterNameProvider() {
        var map = new HashMap<String, Integer>();
        map.put(PENDING_AGG, 2);
        map.put(PENDING_COLLABORATION_AGG, 1);
        map.put(ASSIGNED_AGG, 2);
        map.put(ASSIGNED_COLLABORATION_AGG, 1);
        map.put(APPROVED_AGG, 2);
        map.put(APPROVED_COLLABORATION_AGG, 1);
        map.put(REJECTED_AGG, 2);
        map.put(REJECTED_COLLABORATION_AGG, 1);
        map.put(ASSIGNMENTS_AGG, 4);
        return map.entrySet().stream();
    }

    private static CandidateSearchParameters.Builder defaultSearchParameters() {
        return CandidateSearchParameters.builder()
                   .withAffiliations(List.of())
                   .withCustomer(CUSTOMER).withUsername(USERNAME).withYear(YEAR);
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
