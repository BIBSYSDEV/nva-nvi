package no.sikt.nva.nvi.rest.fetch;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchNviPeriodHandler extends ApiGatewayHandler<Void, NviPeriodDto> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchNviPeriodHandler.class);
    public static final String PERIOD_IDENTIFIER = "periodIdentifier";
    public static final String BAD_GATEWAY_EXCEPTION_MESSAGE = "Something went wrong! Contact application administrator.";
    private final NviService nviService;

    @JacocoGenerated
    public FetchNviPeriodHandler() {
        super(Void.class);
        this.nviService = NviService.defaultNviService();
    }

    public FetchNviPeriodHandler(NviService nviService) {
        super(Void.class);
        this.nviService = nviService;
    }

    @Override
    protected NviPeriodDto processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        return attempt(() -> requestInfo.getPathParameter(PERIOD_IDENTIFIER))
                   .map(nviService::getPeriod)
                   .map(NviPeriodDto::fromNviPeriod)
                   .orElseThrow(this::mapException);
    }

    private <T> ApiGatewayException mapException(Failure<NviPeriodDto> failure) {
        var exception = failure.getException();
        if (exception instanceof PeriodNotFoundException periodNotFoundException) {
            return new NotFoundException(periodNotFoundException.getMessage());
        } else {
            LOGGER.error(exception.getMessage());
            return new BadGatewayException(BAD_GATEWAY_EXCEPTION_MESSAGE);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, NviPeriodDto output) {
        return HttpURLConnection.HTTP_OK;
    }
}