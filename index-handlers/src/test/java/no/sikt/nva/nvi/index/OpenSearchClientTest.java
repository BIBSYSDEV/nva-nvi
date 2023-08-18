package no.sikt.nva.nvi.index;

import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT.JWTBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CachedValueProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.RandomDataGenerator;
import nva.commons.core.ioutils.IoUtils;
import org.apache.http.HttpHost;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.shaded.org.bouncycastle.operator.DefaultSecretKeySizeProvider;

public class OpenSearchClientTest {

    public static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.0.0";
    private static final OpensearchContainer container = new OpensearchContainer(OPEN_SEARCH_IMAGE);
    private static RestClient restClient;
    private static OpenSearchClient openSearchClient;

    public static void setUpTestContainer() {
        container.start();
        restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
    }

    @BeforeAll
    static void init() {
        setUpTestContainer();
        openSearchClient = new OpenSearchClient(restClient, FakeCachedJwtProvider.setup());
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
        var searchResponse = openSearchClient.search(searchTermToQuery(document.identifier()));
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, containsInAnyOrder(document));
    }

    @Test
    void shouldReturnUniqueDocumentFromIndexWhenSearchingByDocumentIdentifier()
        throws InterruptedException, IOException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(singleNviCandidateIndexDocument(), singleNviCandidateIndexDocument(), document);
        var searchResponse = openSearchClient.search(searchTermToQuery(document.identifier()));
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, hasSize(1));
    }

    @Test
    void shouldDeleteIndexAndThrowExceptionWhenSearchingInNonExistentIndex() throws IOException, InterruptedException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(document);
        openSearchClient.deleteIndex();
        assertThrows(OpenSearchException.class,
                     () -> openSearchClient.search(searchTermToQuery(document.identifier())));
    }

    @Test
    void shouldRemoveDocumentFromIndex() throws InterruptedException, IOException {
        var document = singleNviCandidateIndexDocument();
        addDocumentsToIndex(document);
        openSearchClient.removeDocumentFromIndex(document);
        Thread.sleep(2000);
        var searchResponse =
            openSearchClient.search(searchTermToQuery(document.identifier()));
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, hasSize(0));
    }

    @Test
    void shouldReturnAggregationsForApprovalStatus() throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_approved.json"),
                            documentFromString("document_pending.json"),
                            documentFromString("document_rejected.json"));
        var searchResponse = openSearchClient.search(searchTermToQuery("*"));
        var response = SearchResponseDto.fromSearchResponse(searchResponse);
        assertThat(response.aggregations(), is(notNullValue()));
    }

    @Test
    void shouldReturnDocumentsWithContributorWhenFilterByContributor() throws IOException, InterruptedException {
        addDocumentsToIndex(documentFromString("document_approved.json"), documentFromString("document_pending.json")
            , documentFromString("document_rejected.json"));
        var queryString =
            "publicationDetails.contributors.id:\"https://api.dev.nva.aws.unit" + ".no/cristin/person/1136326\"";
        var searchResponse = openSearchClient.search(searchTermToQuery(queryString));
        var response = SearchResponseDto.fromSearchResponse(searchResponse);
        assertThat(response.hits(), hasSize(2));
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

    private static Query searchTermToQuery(String searchTerm) {
        return new Query.Builder().queryString(constructQuery(searchTerm)).build();
    }

    private static QueryStringQuery constructQuery(String searchTerm) {
        return new QueryStringQuery.Builder().query(searchTerm).build();
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomInteger().toString(), randomString(),
                                             randomPublicationDetails(), randomAffiliationList());
    }

    private static List<Affiliation> randomAffiliationList() {
        return IntStream.range(0, 5).boxed().map(i -> randomAffiliation()).toList();
    }

    private static Affiliation randomAffiliation() {
        return new Affiliation(randomString(), Map.of(), randomStatus());
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

    private void addDocumentsToIndex(NviCandidateIndexDocument... documents) throws InterruptedException {
        Arrays.stream(documents).forEach(document -> openSearchClient.addDocumentToIndex(document));
        Thread.sleep(2000);
    }

    public final class FakeCachedJwtProvider {

        public static String TEST_TOKEN =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJPbmxpbmUgSldUIEJ1"
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
