package no.sikt.nva.nvi.index.apigateway;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.ANY_APPLICATION_TYPE;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static com.google.common.net.MediaType.OOXML_SHEET;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.apigateway.utils.ExcelWorkbookUtil.extractLinesInInstitutionIdentifierColumn;
import static no.sikt.nva.nvi.index.apigateway.utils.ExcelWorkbookUtil.fromInputStream;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponse;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponseWithTotal;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_FIRST_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_IDENTIFIER;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_LAST_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CREATOR_SHARE_COUNT;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.DEPARTMENT_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.FACULTY_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.GLOBAL_STATUS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.GROUP_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PAGE_BEGIN;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PAGE_COUNT;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PAGE_END;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.POINTS_FOR_AFFILIATION;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL_POINTS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_TYPE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_IDENTIFIER;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_INSTANCE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_LANGUAGE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_TITLE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLISHED_YEAR;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.REPORTING_YEAR;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.indexDocumentMissingCreatorAffiliationPoints;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.indexDocumentWithoutLanguage;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.indexDocumentWithoutOptionalPublicationChannelData;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.indexDocumentWithoutPages;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.randomCristinOrgUri;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.randomIndexDocumentWith;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.AccessRight.MANAGE_NVI_CANDIDATES;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.apigateway.utils.ExcelWorkbookUtil;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
import no.sikt.nva.nvi.index.xlsx.ExcelWorkbookGenerator;
import no.sikt.nva.nvi.test.IndexDocumentTestUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
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
    private static final int PAGE_SIZE = Integer.parseInt(new Environment().readEnv(
        "INSTITUTION_REPORT_SEARCH_PAGE_SIZE"));
    private static final Path LANGUAGE_LABELS_PATH = Path.of("supportedLanguageLabels.csv");
    private static final String LINE_SEPERATOR = ",";
    private static final Map<String, String> supportedLanguageLabels = readStaticLanguageLabels();
    private static SearchClient<NviCandidateIndexDocument> openSearchClient;
    private ByteArrayOutputStream output;
    private FetchInstitutionReportHandler handler;

    public static Stream<Arguments> listSupportedLanguages() {
        return Stream.of(Arguments.of("http://lexvo.org/id/iso639-3/eng"),
                         Arguments.of("http://lexvo.org/id/iso639-3/nob"),
                         Arguments.of("http://lexvo.org/id/iso639-3/nno"),
                         Arguments.of("http://lexvo.org/id/iso639-3/dan"),
                         Arguments.of("http://lexvo.org/id/iso639-3/fin"),
                         Arguments.of("http://lexvo.org/id/iso639-3/fra"),
                         Arguments.of("http://lexvo.org/id/iso639-3/isl"),
                         Arguments.of("http://lexvo.org/id/iso639-3/ita"),
                         Arguments.of("http://lexvo.org/id/iso639-3/nld"),
                         Arguments.of("http://lexvo.org/id/iso639-3/por"),
                         Arguments.of("http://lexvo.org/id/iso639-3/rus"),
                         Arguments.of("http://lexvo.org/id/iso639-3/sme"),
                         Arguments.of("http://lexvo.org/id/iso639-3/spa"),
                         Arguments.of("http://lexvo.org/id/iso639-3/swe"),
                         Arguments.of("http://lexvo.org/page/iso639-3/deu"));
    }

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        openSearchClient = mock(OpenSearchClient.class);
        handler = new FetchInstitutionReportHandler(openSearchClient);
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
    @MethodSource("listSupportedLanguages")
    void shouldReturnReportWithLanguageLabel(String languageUri) throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        var candidatesInIndex = List.of(IndexDocumentTestUtils.indexDocumentWithLanguage(CURRENT_YEAR,
                                                                                         topLevelCristinOrg,
                                                                                         languageUri));
        when(openSearchClient.search(any())).thenReturn(createSearchResponse(candidatesInIndex));
        var expectedLanguageLabel = supportedLanguageLabels.get(languageUri);
        handler.handleRequest(requestWithMediaType(MICROSOFT_EXCEL.toString(), topLevelCristinOrg), output, CONTEXT);
        var decodedResponse = Base64.getDecoder().decode(fromOutputStream(output, String.class).getBody());
        var actualLanguages = ExcelWorkbookUtil.extractLinesInLanguageColumn(new ByteArrayInputStream(decodedResponse));
        assertThat(actualLanguages, Matchers.contains(expectedLanguageLabel));
    }

    @Test
    void shouldFetchCandidatesWithPagination()
        throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        var candidatesInIndex = mockTwoCandidatesInIndex(topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(MICROSOFT_EXCEL.toString(), topLevelCristinOrg), output, CONTEXT);

        var firstExpectedResultParameters = SearchResultParameters.builder().withSize(PAGE_SIZE).build();
        var secondExpectedResultParameters = SearchResultParameters.builder()
                                                 .withSize(PAGE_SIZE)
                                                 .withOffset(PAGE_SIZE)
                                                 .build();

        verify(openSearchClient, times(1)).search(eq(buildRequest(topLevelCristinOrg, firstExpectedResultParameters)));
        verify(openSearchClient, times(1)).search(eq(buildRequest(topLevelCristinOrg, secondExpectedResultParameters)));
        var decodedResponse = Base64.getDecoder().decode(fromOutputStream(output, String.class).getBody());
        var actual = fromInputStream(new ByteArrayInputStream(decodedResponse));
        var expected = getExpectedReport(candidatesInIndex, topLevelCristinOrg);
        assertEquals(expected, actual);
    }

    @Test
    void shouldNotFailWhenCandidatesAreMissingOptionalData() throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        var candidatesInIndex = mockCandidatesWithoutOptionalDataInOpenSearch(topLevelCristinOrg);
        var expected = getExpectedReport(candidatesInIndex, topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(MICROSOFT_EXCEL.toString(), topLevelCristinOrg), output, CONTEXT);

        var decodedResponse = Base64.getDecoder().decode(fromOutputStream(output, String.class).getBody());
        var actual = fromInputStream(new ByteArrayInputStream(decodedResponse));
        assertEquals(expected, actual);
    }

    @Test
    void shouldPerformSearchForGivenInstitutionAndYear() throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        var year = "2021";
        var request = createRequest(topLevelCristinOrg, MANAGE_NVI_CANDIDATES, topLevelCristinOrg,
                                    Map.of(YEAR, year)).build();
        handler.handleRequest(request, output, CONTEXT);

        var expectedSearchParameters = CandidateSearchParameters.builder()
                                           .withYear(year)
                                           .withTopLevelCristinOrg(topLevelCristinOrg)
                                           .withAffiliations(List.of(extractIdentifier(topLevelCristinOrg)))
                                           .withSearchResultParameters(
                                               SearchResultParameters.builder().withSize(PAGE_SIZE).build())
                                           .build();
        verify(openSearchClient).search(eq(expectedSearchParameters));
    }

    @Test
    void shouldNotContainDataForOtherInstitutions()
        throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        mockCandidatesInOpenSearch(topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(MICROSOFT_EXCEL.toString(), topLevelCristinOrg), output, CONTEXT);

        var decodedResponse = Base64.getDecoder().decode(fromOutputStream(output, String.class).getBody());
        var actual = new HashSet<>(
            extractLinesInInstitutionIdentifierColumn(new ByteArrayInputStream(decodedResponse)));
        assertEquals(1, actual.size());
    }

    @Test
    void shouldLogCandidateIdOnFailure() throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        var indexDocument = indexDocumentMissingCreatorAffiliationPoints(CURRENT_YEAR, topLevelCristinOrg);
        when(openSearchClient.search(any())).thenReturn(createSearchResponse(indexDocument));
        var appender = LogUtils.getTestingAppender(NviCandidateIndexDocument.class);
        handler.handleRequest(requestWithMediaType(MICROSOFT_EXCEL.toString(), topLevelCristinOrg), output, CONTEXT);
        assertTrue(appender.getMessages().contains(indexDocument.id().toString()));
    }

    @ParameterizedTest
    @MethodSource("listSupportedMediaTypes")
    void shouldReturnRequestedContentType(String mediaType) throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        mockCandidatesInOpenSearch(topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(mediaType, topLevelCristinOrg), output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(mediaType));
    }

    @ParameterizedTest
    @MethodSource("listSupportedMediaTypes")
    void shouldReturnBase64EncodedOutputStream(String mediaType) throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        mockCandidatesInOpenSearch(topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(mediaType, topLevelCristinOrg), output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getIsBase64Encoded());
    }

    @Test
    void shouldReturnMediaTypeOpenXmlOfficeDocumentAsDefault() throws IOException {
        var topLevelCristinOrg = randomCristinOrgUri();
        mockCandidatesInOpenSearch(topLevelCristinOrg);

        handler.handleRequest(requestWithMediaType(ANY_APPLICATION_TYPE.toString(), topLevelCristinOrg), output,
                              CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, String.class);
        assertThat(response.getHeaders().get(CONTENT_TYPE), is(OOXML_SHEET.toString()));
    }

    private static Map<String, String> readStaticLanguageLabels() {
        return IoUtils.linesfromResource(LANGUAGE_LABELS_PATH)
                   .stream()
                   .map(FetchInstitutionReportHandlerTest::splitByLineSeperator)
                   .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
    }

    private static String[] splitByLineSeperator(String line) {
        return line.split(LINE_SEPERATOR);
    }

    private static List<NviCandidateIndexDocument> mockTwoCandidatesInIndex(URI topLevelCristinOrg)
        throws IOException {
        var firstDocument = randomIndexDocumentWith(CURRENT_YEAR, topLevelCristinOrg);
        var secondDocument = randomIndexDocumentWith(CURRENT_YEAR, topLevelCristinOrg);
        var candidatesInIndex = List.of(firstDocument, secondDocument);
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponseWithTotal(firstDocument, candidatesInIndex.size()))
            .thenReturn(createSearchResponseWithTotal(secondDocument, candidatesInIndex.size()));
        return candidatesInIndex;
    }

    private static CandidateSearchParameters buildRequest(URI topLevelCristinOrg,
                                                          SearchResultParameters resultParameters) {
        return CandidateSearchParameters.builder()
                   .withYear(String.valueOf(CURRENT_YEAR))
                   .withTopLevelCristinOrg(topLevelCristinOrg)
                   .withAffiliations(List.of(extractIdentifier(topLevelCristinOrg)))
                   .withSearchResultParameters(resultParameters)
                   .build();
    }

    private static String extractIdentifier(URI topLevelCristinOrg) {
        return UriWrapper.fromUri(topLevelCristinOrg).getLastPathElement();
    }

    private static Stream<Arguments> listSupportedMediaTypes() {
        return Stream.of(Arguments.of(MICROSOFT_EXCEL.toString()),
                         Arguments.of(OOXML_SHEET.toString()));
    }

    private static List<String> getExpectedHeaders() {
        return List.of(REPORTING_YEAR.getValue(),
                       PUBLICATION_IDENTIFIER.getValue(),
                       PUBLISHED_YEAR.getValue(),
                       INSTITUTION_APPROVAL_STATUS.getValue(),
                       PUBLICATION_INSTANCE.getValue(),
                       PUBLICATION_CHANNEL.getValue(),
                       PUBLICATION_CHANNEL_TYPE.getValue(),
                       PUBLICATION_CHANNEL_LEVEL.getValue(),
                       CONTRIBUTOR_IDENTIFIER.getValue(),
                       INSTITUTION_ID.getValue(),
                       FACULTY_ID.getValue(),
                       DEPARTMENT_ID.getValue(),
                       GROUP_ID.getValue(),
                       CONTRIBUTOR_LAST_NAME.getValue(),
                       CONTRIBUTOR_FIRST_NAME.getValue(),
                       PUBLICATION_CHANNEL_NAME.getValue(),
                       PAGE_BEGIN.getValue(),
                       PAGE_END.getValue(),
                       PAGE_COUNT.getValue(),
                       PUBLICATION_TITLE.getValue(),
                       PUBLICATION_LANGUAGE.getValue(),
                       GLOBAL_STATUS.getValue(),
                       PUBLICATION_CHANNEL_LEVEL_POINTS.getValue(),
                       INTERNATIONAL_COLLABORATION_FACTOR.getValue(),
                       CREATOR_SHARE_COUNT.getValue(),
                       POINTS_FOR_AFFILIATION.getValue());
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

    private static boolean isPartOfOrEqualToTopLevelOrganization(URI topLevelCristinOrg, NviOrganization affiliation) {
        return affiliation.partOf().contains(topLevelCristinOrg) || affiliation.id().equals(topLevelCristinOrg);
    }

    private static String getNumberOfPages(Pages pages) {
        return nonNull(pages) && nonNull(pages.numberOfPages()) ? pages.numberOfPages() : EMPTY_STRING;
    }

    private static String getPagesEnd(Pages pages) {
        return nonNull(pages) && nonNull(pages.end()) ? pages.end() : EMPTY_STRING;
    }

    private static String getPagesBegin(Pages pages) {
        return nonNull(pages) && nonNull(pages.begin()) ? pages.begin() : EMPTY_STRING;
    }

    private static String getExpectedLanguageLabel(NviCandidateIndexDocument document) {
        var languageUri = document.publicationDetails().language();
        return Optional.ofNullable(supportedLanguageLabels.get(languageUri)).orElse("Andre språk");
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
        return document.getNviContributors().stream()
                   .map(nviContributor -> getExpectedRowsForContributorAffiliations(document, topLevelCristinOrg,
                                                                                    nviContributor))
                   .flatMap(List::stream);
    }

    private List<List<String>> getExpectedRowsForContributorAffiliations(NviCandidateIndexDocument document,
                                                                         URI topLevelCristinOrg,
                                                                         NviContributor nviContributor) {
        return nviContributor.nviAffiliations().stream()
                   .filter(affiliation -> isPartOfOrEqualToTopLevelOrganization(topLevelCristinOrg, affiliation))
                   .map(affiliation -> getExpectedRow(document, nviContributor, affiliation, topLevelCristinOrg))
                   .toList();
    }

    private List<String> getExpectedRow(NviCandidateIndexDocument document, NviContributor nviContributor,
                                        NviOrganization affiliation, URI topLevelCristinOrg) {
        var expectedRow = new ArrayList<String>();
        expectedRow.add(document.reportingPeriod().year());
        expectedRow.add(document.publicationIdentifier());
        expectedRow.add(document.publicationDetails().publicationDate().year());
        expectedRow.add(getExpectedApprovalStatus(document.getApprovalStatusForInstitution(topLevelCristinOrg)));
        expectedRow.add(document.publicationDetails().type());
        var publicationChannel = document.publicationDetails().publicationChannel();
        expectedRow.add(nonNull(publicationChannel.id()) ? publicationChannel.id().toString() : EMPTY_STRING);
        expectedRow.add(nonNull(publicationChannel.type()) ? publicationChannel.type() : EMPTY_STRING);
        expectedRow.add(publicationChannel.scientificValue().getValue());
        expectedRow.add(nviContributor.id());
        expectedRow.add(affiliation.getInstitutionIdentifier());
        expectedRow.add(affiliation.getFacultyIdentifier());
        expectedRow.add(affiliation.getDepartmentIdentifier());
        expectedRow.add(affiliation.getGroupIdentifier());
        expectedRow.add(nviContributor.name());
        expectedRow.add(nviContributor.name());
        expectedRow.add(nonNull(publicationChannel.name()) ? publicationChannel.name() : EMPTY_STRING);
        expectedRow.add(getPagesBegin(document.publicationDetails().pages()));
        expectedRow.add(getPagesEnd(document.publicationDetails().pages()));
        expectedRow.add(getNumberOfPages(document.publicationDetails().pages()));
        expectedRow.add(document.publicationDetails().title());
        expectedRow.add(getExpectedLanguageLabel(document));
        expectedRow.add(getExpectedGlobalApprovalStatus(document.globalApprovalStatus()));
        expectedRow.add(document.publicationTypeChannelLevelPoints().toString());
        expectedRow.add(document.internationalCollaborationFactor().toString());
        expectedRow.add(String.valueOf(document.creatorShareCount()));
        expectedRow.add(
            document.getPointsForContributorAffiliation(topLevelCristinOrg, nviContributor, affiliation).toString());
        return expectedRow;
    }

    private String getExpectedGlobalApprovalStatus(GlobalApprovalStatus globalApprovalStatus) {
        return switch (globalApprovalStatus) {
            case PENDING -> "?";
            case APPROVED -> "J";
            case REJECTED -> "N";
            case DISPUTE -> "T";
        };
    }

    private String getExpectedApprovalStatus(ApprovalStatus approvalStatus) {
        return switch (approvalStatus) {
            case APPROVED -> "J";
            case REJECTED -> "N";
            case NEW, PENDING -> "?";
        };
    }

    private List<NviCandidateIndexDocument> mockCandidatesInOpenSearch(URI topLevelCristinOrg) throws IOException {
        var indexDocuments = List.of(randomIndexDocumentWith(CURRENT_YEAR, topLevelCristinOrg));
        when(openSearchClient.search(any())).thenReturn(createSearchResponse(indexDocuments));
        return indexDocuments;
    }

    private List<NviCandidateIndexDocument> mockCandidatesWithoutOptionalDataInOpenSearch(URI topLevelCristinOrg)
        throws IOException {
        var indexDocuments = List.of(indexDocumentWithoutPages(CURRENT_YEAR, topLevelCristinOrg),
                                     indexDocumentWithoutOptionalPublicationChannelData(CURRENT_YEAR,
                                                                                        topLevelCristinOrg),
                                     indexDocumentWithoutLanguage(CURRENT_YEAR, topLevelCristinOrg));
        when(openSearchClient.search(any())).thenReturn(createSearchResponse(indexDocuments));
        return indexDocuments;
    }
}
