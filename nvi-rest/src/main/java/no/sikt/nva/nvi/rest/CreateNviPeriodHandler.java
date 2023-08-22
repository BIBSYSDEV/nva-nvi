package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.rest.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.rest.utils.RequestUtil;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.NviPeriod.Builder;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public class CreateNviPeriodHandler extends ApiGatewayHandler<NviPeriod, NviPeriod> {

    private final NviService nviService;

    public CreateNviPeriodHandler(NviService nviService) {
        super(NviPeriod.class);
        this.nviService = nviService;
    }

    @Override
    protected NviPeriod processInput(NviPeriod input, RequestInfo requestInfo, Context context)
        throws UnauthorizedException {

        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_PERIODS);

        return attempt(input::copy)
                   .map(builder -> builder.withCreatedBy(getUsername(requestInfo)))
                   .map(Builder::build)
                   .map(nviService::createPeriod)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(NviPeriod input, NviPeriod output) {
        return HttpURLConnection.HTTP_CREATED;
    }
}



