package no.sikt.nva.nvi.rest.fetch;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.List;
import no.sikt.nva.nvi.common.db.model.PeriodDao.PeriodData;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.NviPeriodDto;
import no.sikt.nva.nvi.rest.model.NviPeriodsResponse;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class FetchNviPeriodsHandler extends ApiGatewayHandler<Void, NviPeriodsResponse> {

    private final NviService nviService;

    @JacocoGenerated
    public FetchNviPeriodsHandler() {
        super(Void.class);
        this.nviService = NviService.defaultNviService();
    }

    public FetchNviPeriodsHandler(NviService nviService) {
        super(Void.class);
        this.nviService = nviService;
    }

    @Override
    protected NviPeriodsResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_PERIODS);

        return attempt(nviService::getPeriods)
                   .map(this::toNviPeriodsResponse)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, NviPeriodsResponse output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static List<NviPeriodDto> toPeriodDtoList(List<PeriodData> periodData) {
        return periodData.stream()
                   .map(NviPeriodDto::fromNviPeriod)
                   .toList();
    }

    private NviPeriodsResponse toNviPeriodsResponse(List<PeriodData> periodData) {
        return new NviPeriodsResponse(toPeriodDtoList(periodData));
    }
}
