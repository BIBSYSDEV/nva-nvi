package no.sikt.nva.nvi.index.apigateway;

import static java.lang.Integer.parseInt;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.net.MediaType;
import java.net.HttpURLConnection;
import java.time.Year;
import java.util.List;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.utils.InstitutionReportGenerator;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;

public class FetchInstitutionReportHandler extends ApiGatewayHandler<Void, String> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(FetchInstitutionReportHandler.class);
    private static final String PATH_PARAMETER_YEAR = "year";
    private static final String ENV_VAR_INSTITUTION_REPORT_SEARCH_PAGE_SIZE = "INSTITUTION_REPORT_SEARCH_PAGE_SIZE";
    private final SearchClient<NviCandidateIndexDocument> searchClient;

    @JacocoGenerated
    public FetchInstitutionReportHandler() {
        this(OpenSearchClient.defaultOpenSearchClient());
    }

    public FetchInstitutionReportHandler(SearchClient<NviCandidateIndexDocument> searchClient) {
        super(Void.class);
        this.searchClient = searchClient;
    }

    @Override
    protected List<MediaType> listSupportedMediaTypes() {
        // The first media type in the list is the default media type if the client does not specify a media type
        // See RestRequestHandler#defaultResponseContentTypeWhenNotSpecifiedByClientRequest
        return List.of(MediaType.OOXML_SHEET, MediaType.MICROSOFT_EXCEL);
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        if (!requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI_CANDIDATES)) {
            throw new UnauthorizedException();
        }
        if (isInvalidPathParameterYear(requestInfo)) {
            throw new BadRequestException("Invalid path parameter 'year'");
        }
    }

    @Override
    protected String processInput(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        setIsBase64Encoded(true);
        var year = requestInfo.getPathParameter(PATH_PARAMETER_YEAR);
        var institutionId = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        logger.info("Generating report for institution {} for year {}", institutionId, year);
        var pageSize = parseInt(new Environment().readEnv(ENV_VAR_INSTITUTION_REPORT_SEARCH_PAGE_SIZE));
        return new InstitutionReportGenerator(searchClient, pageSize, year, institutionId).generateReport()
                   .toBase64EncodedString();
    }

    @Override
    protected Integer getSuccessStatusCode(Void unused, String o) {
        return HttpURLConnection.HTTP_OK;
    }

    private static boolean isInvalidPathParameterYear(RequestInfo requestInfo) {
        return attempt(() -> Year.of(parseInt(requestInfo.getPathParameter(PATH_PARAMETER_YEAR)))).isFailure();
    }
}
