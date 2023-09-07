package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.rest.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod.Builder;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.utils.RequestUtil;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class UpdateNviPeriodHandler extends ApiGatewayHandler<NviPeriodDto, NviPeriodDto> {

    private final NviService nviService;

    @JacocoGenerated
    public UpdateNviPeriodHandler() {
        super(NviPeriodDto.class);
        this.nviService = NviService.defaultNviService();
    }

    public UpdateNviPeriodHandler(NviService nviService) {
        super(NviPeriodDto.class);
        this.nviService = nviService;
    }

    @Override
    protected NviPeriodDto processInput(NviPeriodDto input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_PERIODS);

        return attempt(input::toNviPeriod)
                   .map(DbNviPeriod::copy)
                   .map(builder -> builder.modifiedBy(getUsername(requestInfo)))
                   .map(Builder::build)
                   .map(nviService::updatePeriod)
                   .map(NviPeriodDto::fromNviPeriod)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviPeriodDto input, NviPeriodDto output) {
        return HttpURLConnection.HTTP_OK;
    }
}
