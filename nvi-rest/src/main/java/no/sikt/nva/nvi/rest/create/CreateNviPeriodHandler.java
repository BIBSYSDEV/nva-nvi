package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod.Builder;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import no.sikt.nva.nvi.rest.model.User;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class CreateNviPeriodHandler extends ApiGatewayHandler<NviPeriodDto, NviPeriodDto> {

    private final NviService nviService;

    @JacocoGenerated
    public CreateNviPeriodHandler() {
        super(NviPeriodDto.class);
        this.nviService = NviService.defaultNviService();
    }

    public CreateNviPeriodHandler(NviService nviService) {
        super(NviPeriodDto.class);
        this.nviService = nviService;
    }

    @Override
    protected NviPeriodDto processInput(NviPeriodDto input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_PERIODS);

        return attempt(input::toNviPeriod)
                   .map(DbNviPeriod::copy)
                   .map(builder -> builder.createdBy(getUsername(requestInfo)))
                   .map(Builder::build)
                   .map(nviService::createPeriod)
                   .map(NviPeriodDto::fromNviPeriod)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviPeriodDto input, NviPeriodDto output) {
        return HttpURLConnection.HTTP_CREATED;
    }
}



