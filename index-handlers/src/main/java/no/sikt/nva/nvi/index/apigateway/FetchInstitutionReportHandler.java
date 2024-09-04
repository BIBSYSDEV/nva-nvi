package no.sikt.nva.nvi.index.apigateway;

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
import no.sikt.nva.nvi.index.utils.TooManyHitsException;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;

public class FetchInstitutionReportHandler extends ApiGatewayHandler<Void, String> {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(FetchInstitutionReportHandler.class);
    private static final String PATH_PARAMETER_YEAR = "year";
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
        try {
            return new InstitutionReportGenerator(searchClient, year, institutionId).generateReport()
                       .toBase64EncodedString();
        } catch (TooManyHitsException exception) {
            logger.error("Failed to generate report for institution {} for year {}. Error: {}", institutionId, year,
                         exception.getMessage());
            throw exception;
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void unused, String o) {
        return HttpURLConnection.HTTP_OK;
    }

    private static boolean isInvalidPathParameterYear(RequestInfo requestInfo) {
        return attempt(() -> Year.of(Integer.parseInt(requestInfo.getPathParameter(PATH_PARAMETER_YEAR)))).isFailure();
    }
}
