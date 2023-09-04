package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.Aggregations.COMPLETED_AGGREGATION_NAME;
import static no.sikt.nva.nvi.index.Aggregations.TOTAL_COUNT_AGGREGATION_NAME;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.ioutils.IoUtils;
import org.apache.http.HttpHost;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.testcontainers.OpensearchContainer;

public class OpenSearchClientTest {

    private static final URI CUSTOMER = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    private static final String USERNAME = "user1";
    public static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.0.0";
    private static final OpensearchContainer container = new OpensearchContainer(OPEN_SEARCH_IMAGE);
    public static final String DOC_COUNT = "docCount";
    private static RestClient restClient;
    private static OpenSearchClient openSearchClient;

    public static void setUpTestContainer() {
        container.start();
        restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
    }

    @BeforeAll
    public static void init() throws JsonProcessingException, InterruptedException {
        setUpTestContainer();
        openSearchClient = new OpenSearchClient(restClient, FakeCachedJwtProvider.setup());
        addDocumentsToIndex(documentFromString("document_pending.json"),
                            documentFromString("document_pending_collaboration.json"),
                            documentFromString("document_assigned.json"),
                            documentFromString("document_assigned_collaboration.json"),
                            documentFromString("document_approved.json"),
                            documentFromString("document_approved_collaboration.json"),
                            documentFromString("document_rejected.json"),
                            documentFromString("document_rejected_collaboration.json"));
    }

    @AfterAll
    static void afterAll() {
        container.stop();
    }

    @Test
    void shouldCreateIndexAndAddDocumentToIndexWhenIndexDoesNotExist() throws IOException, InterruptedException {
        var document = singleNviCandidateIndexDocument();
        openSearchClient.addDocumentToIndex(document);
        Thread.sleep(2000);
        var searchResponse =
            openSearchClient.search(document.identifier(), null, USERNAME, CUSTOMER);
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, containsInAnyOrder(document));
    }

    @Test
    void shouldReturnUniqueDocumentFromIndexWhenSearchingByDocumentIdentifier()
        throws InterruptedException, IOException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(singleNviCandidateIndexDocument(), singleNviCandidateIndexDocument(), document);
        var searchResponse =
            openSearchClient.search(document.identifier(), null, USERNAME, CUSTOMER);
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, hasSize(1));
    }

    @Test
    void shouldDeleteIndexAndThrowExceptionWhenSearchingInNonExistentIndex() throws IOException, InterruptedException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(document);
        openSearchClient.deleteIndex();
        assertThrows(OpenSearchException.class,
                     () -> openSearchClient.search(document.identifier(), null, USERNAME, CUSTOMER));
    }

    @Test
    void shouldRemoveDocumentFromIndex() throws InterruptedException, IOException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(document);
        openSearchClient.removeDocumentFromIndex(document);
        Thread.sleep(2000);
        var searchResponse =
            openSearchClient.search(document.identifier(), null, USERNAME, CUSTOMER);
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, hasSize(0));
    }

    @ParameterizedTest
    @MethodSource("aggregationNameAndExpectedCountProvider")
    void shouldReturnAggregationsWithExpectedCount(Entry<String, Integer> entry) throws IOException {


        var searchResponse =
            openSearchClient.search("*", null, USERNAME, CUSTOMER);
        var response = SearchResponseDto.fromSearchResponse(searchResponse);
        var docCount = getDocCount(response, entry.getKey());

        assertThat(docCount, is(equalTo(entry.getValue())));
    }


    @Test
    void shouldReturnSearchResultsUsingFilterAndSearchTermCombined() throws IOException {
        var searchTerm = "Testing nvi flow 36";
        var customer = "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0";
        var filter = "pending";
        var searchResponse = openSearchClient.search(
            searchTerm, filter, randomString(), URI.create(customer));
        assertThat(searchResponse.hits().hits(), hasSize(2));
    }

    private static int getDocCount(SearchResponseDto response, String aggregationName) {
        return response.aggregations().get(aggregationName).get(DOC_COUNT).asInt();
    }

    private static NviCandidateIndexDocument documentFromString(String fileName) throws JsonProcessingException {
        var string = IoUtils.stringFromResources(Path.of(fileName));
        return JsonUtils.dtoObjectMapper.readValue(string, NviCandidateIndexDocument.class);
    }

    @NotNull
    private static List<NviCandidateIndexDocument> searchResponseToIndexDocumentList(
        SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().stream().map(Hit::source).toList();
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        var approvals = randomApprovalList();
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomPublicationDetails(),
                                             approvals, approvals.size());
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
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    private static void addDocumentsToIndex(NviCandidateIndexDocument... documents) throws InterruptedException {
        Arrays.stream(documents).forEach(document -> openSearchClient.addDocumentToIndex(document));
        Thread.sleep(2000);
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
        map.put(COMPLETED_AGGREGATION_NAME, 4);
        map.put(TOTAL_COUNT_AGGREGATION_NAME, 8);
        return map.entrySet().stream();
    }

    public final class FakeCachedJwtProvider {

        public static String TEST_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJPbmxpbmUgSldUIEJ1"
                                          + "aWxkZXIiLCJpYXQiOjE2Njg1MTE4NTcsImV4cCI6MTcwMDA0Nzg1NywiYXVkIjoi"
                                          + "d3d3LmV4YW1wbGUuY29tIiwic3ViIjoianJvY2tldEBleGFtcGxlLmNvbSIsIkdpd"
                                          + "mVuTmFtZSI6IkpvaG5ueSIsIlN1cm5hbWUiOiJSb2NrZXQiLCJFbWFpbCI6Impyb2"
                                          + "NrZXRAZXhhbXBsZS5jb20iLCJSb2xlIjoiTWFuYWdlciIsInNjb3BlIjoiZXhhbX"
                                          + "BsZS1zY29wZSJ9.ne8Jb4f2xao1zSJFZxIBRrh4WFNjkaBRV3-Ybp6fHZU";

        public static CachedJwtProvider setup() {
            var jwt = mock(DecodedJWT.class);
            var cogintoAuthenticatorMock = mock(CognitoAuthenticator.class);

            when(jwt.getToken()).thenReturn(TEST_TOKEN);
            when(jwt.getExpiresAt()).thenReturn(Date.from(Instant.now().plus(Duration.ofMinutes(5))));
            when(cogintoAuthenticatorMock.fetchBearerToken()).thenReturn(jwt);

            return new CachedJwtProvider(cogintoAuthenticatorMock, Clock.systemDefaultZone());
        }
    }
}
