package no.sikt.nva.nvi.index.apigateway;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.ANY_APPLICATION_TYPE;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static com.google.common.net.MediaType.OOXML_SHEET;
import static no.sikt.nva.nvi.index.apigateway.NviInstitutionStatusHeader.getExpectedHeaders;
import static no.sikt.nva.nvi.index.apigateway.utils.ExcelWorkbookUtil.fromInputStream;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponse;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.randomCristinOrgUri;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import no.sikt.nva.nvi.test.IndexDocumentTestUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.paths.UriWrapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.zalando.problem.Problem;

public class FetchInstitutionReportHandlerTest {

    private static final String YEAR = "year";
    private static final int CURRENT_YEAR = Year.now().getValue();
    private static final Context CONTEXT = mock(Context.class);
    private static SearchClient<NviCandidateIndexDocument> openSearchClient;
    private ByteArrayOutputStream output;
    private FetchInstitutionReportHandler handler;

    public static Stream<Arguments> listSupportedMediaTypes() {
        return Stream.of(Arguments.of(MICROSOFT_EXCEL.toString()),
                         Arguments.of(OOXML_SHEET.toString()));
    }

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        handler = new FetchInstitutionReportHandler();
        openSearchClient = mock(OpenSearchClient.class);
    }

    @Test
    void shouldReturnUnauthorizedWhenUserDoesNotHaveSufficientAccessRight() throws IOException {
        var institutionId = randomUri();
        var customerId = randomUri();
        var request = createRequest(institutionId, AccessRight.MANAGE_DOI, customerId,
                                    Map.of(YEAR, String.valueOf(CURRENT_YEAR))).build();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnBadRequestIfPathParamYearIsInvalid() throws IOException {
        var request = requestWithInvalidYearParam();
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @ParameterizedTest
    @MethodSource("listSupportedMediaTypes")
    void shouldReturnCandidatesWithApprovalsBelongingToUsersTopLevelOrganization(String mediaType)
        throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        var candidatesInIndex = mockCandidatesInOpenSearch(topLevelCristinOrg);
        var expected = getExpectedReport(candidatesInIndex, topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(mediaType, topLevelCristinOrg), output, CONTEXT);

        var decodedResponse = Base64.getDecoder().decode(fromOutputStream(output, String.class).getBody());
        var actual = fromInputStream(new ByteArrayInputStream(decodedResponse));
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("listSupportedMediaTypes")
    void shouldReturnRequestedContentType(String mediaType) throws IOException {
        var request = requestWithMediaType(mediaType, randomUri());
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(mediaType));
    }

    @ParameterizedTest
    @MethodSource("listSupportedMediaTypes")
    void shouldReturnBase64EncodedOutputStream(String mediaType) throws IOException {
        var request = requestWithMediaType(mediaType, randomUri());
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getIsBase64Encoded());
    }

    @Test
    void shouldReturnMediaTypeOpenXmlOfficeDocumentAsDefault() throws IOException {
        var request = requestWithMediaType(ANY_APPLICATION_TYPE.toString(), randomUri());
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(OOXML_SHEET.toString()));
    }

    private static InputStream requestWithInvalidYearParam() throws JsonProcessingException {
        var invalidYear = "someInvalidYear";
        return createRequest(randomUri(), MANAGE_NVI_CANDIDATES, randomUri(), Map.of())
                   .withPathParameters(Map.of(YEAR, invalidYear))
                   .build();
    }

    private static InputStream requestWithMediaType(String mediaType, URI topLevelCristinOrg)
        throws JsonProcessingException {
        return createRequest(topLevelCristinOrg, MANAGE_NVI_CANDIDATES, randomUri(),
                             Map.of(YEAR, String.valueOf(CURRENT_YEAR)))
                   .withHeaders(Map.of(ACCEPT, mediaType))
                   .build();
    }

    private static HandlerRequestBuilder<InputStream> createRequest(URI topLevelCristinOrg,
                                                                    AccessRight accessRight,
                                                                    URI customerId,
                                                                    Map<String, String> pathParameters) {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withCurrentCustomer(customerId)
                   .withAccessRights(customerId, accessRight)
                   .withTopLevelCristinOrgId(topLevelCristinOrg)
                   .withUserName(randomString())
                   .withPathParameters(pathParameters);
    }

    private ExcelWorkbookGenerator getExpectedReport(List<NviCandidateIndexDocument> candidatesInIndex,
                                                     URI topLevelCristinOrg) {
        var headers = getExpectedHeaders();
        var data = getExpectedRows(candidatesInIndex, topLevelCristinOrg);
        return new ExcelWorkbookGenerator(headers, data);
    }

    private List<List<String>> getExpectedRows(List<NviCandidateIndexDocument> candidatesInIndex,
                                               URI topLevelCristinOrg) {
        return candidatesInIndex.stream()
                   .flatMap(document -> getExpectedRows(document, topLevelCristinOrg))
                   .toList();
    }

    private Stream<List<String>> getExpectedRows(NviCandidateIndexDocument document, URI topLevelCristinOrg) {
        return document.publicationDetails().contributors().stream()
                   .filter(NviContributor.class::isInstance)
                   .map(NviContributor.class::cast)
                   .map(nviContributor -> getExpectedRowsForContributorAffiliations(document, topLevelCristinOrg,
                                                                                    nviContributor))
                   .flatMap(List::stream);
    }

    private List<List<String>> getExpectedRowsForContributorAffiliations(NviCandidateIndexDocument document,
                                                                         URI topLevelCristinOrg,
                                                                         NviContributor nviContributor) {
        return nviContributor.affiliations()
                   .stream()
                   .filter(NviOrganization.class::isInstance)
                   .map(NviOrganization.class::cast)
                   .map(affiliation -> getExpectedRow(document, nviContributor, affiliation, topLevelCristinOrg))
                   .toList();
    }

    private List<String> getExpectedRow(NviCandidateIndexDocument document, NviContributor nviContributor,
                                        NviOrganization affiliation, URI topLevelCristinOrg) {
        var expectedRow = new ArrayList<String>();
        expectedRow.add(document.reportingPeriod().year());
        expectedRow.add(document.publicationDetails().id());
        expectedRow.add(document.publicationDetails().publicationDate().year());
        expectedRow.add(document.getApprovalForInstitution(topLevelCristinOrg).toString());
        expectedRow.add(document.publicationDetails().type());
        expectedRow.add(nviContributor.id());
        expectedRow.add(affiliation.getInstitutionIdentifier());
        expectedRow.add(affiliation.getFacultyIdentifier());
        expectedRow.add(affiliation.getDepartmentIdentifier());
        expectedRow.add(affiliation.getGroupIdentifier());
        expectedRow.add(nviContributor.name());
        expectedRow.add(nviContributor.name());
        expectedRow.add(document.publicationDetails().title());
        expectedRow.add(document.globalApprovalStatus().toString());
        return expectedRow;
    }

    private List<NviCandidateIndexDocument> mockCandidatesInOpenSearch(URI topLevelCristinOrg) throws IOException {
        var indexDocument = IndexDocumentTestUtils.randomIndexDocument(CURRENT_YEAR, topLevelCristinOrg);
        when(openSearchClient.search(any())).thenReturn(createSearchResponse(indexDocument));
        return List.of(indexDocument);
    }
}
