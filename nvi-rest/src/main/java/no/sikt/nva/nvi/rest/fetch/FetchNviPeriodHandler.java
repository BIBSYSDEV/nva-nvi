package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
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
    private static final String PERIOD_IDENTIFIER = "periodIdentifier";
    private static final String BAD_GATEWAY_EXCEPTION_MESSAGE = "Something went wrong! Contact application "
                                                                + "administrator.";
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public FetchNviPeriodHandler() {
        this(new PeriodRepository(defaultDynamoClient()));
    }

    public FetchNviPeriodHandler(PeriodRepository periodRepository) {
        super(Void.class);
        this.periodRepository = periodRepository;
    }

    @Override
    protected void validateRequest(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {

    }

    @Override
    protected NviPeriodDto processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        return attempt(() -> requestInfo.getPathParameter(PERIOD_IDENTIFIER))
                   .map(period -> NviPeriod.fetchByPublishingYear(period, periodRepository))
                   .map(NviPeriod::toDto)
                   .orElseThrow(this::mapException);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, NviPeriodDto output) {
        return HttpURLConnection.HTTP_OK;
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
}