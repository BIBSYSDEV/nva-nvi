package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.List;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.rest.model.NviPeriodsResponse;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class FetchNviPeriodsHandler extends ApiGatewayHandler<Void, NviPeriodsResponse> {

    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public FetchNviPeriodsHandler() {
        this(new PeriodRepository(defaultDynamoClient()));
    }

    public FetchNviPeriodsHandler(PeriodRepository periodRepository) {
        super(Void.class);
        this.periodRepository = periodRepository;
    }

    @Override
    protected NviPeriodsResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI);

        return attempt(() -> NviPeriod.fetchAll(periodRepository))
                   .map(this::toNviPeriodsResponse)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, NviPeriodsResponse output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static List<UpsertNviPeriodRequest> toPeriodDtoList(List<DbNviPeriod> dbNviPeriods) {
        return dbNviPeriods.stream()
                   .map(UpsertNviPeriodRequest::fromNviPeriod)
                   .toList();
    }

    private NviPeriodsResponse toNviPeriodsResponse(List<NviPeriod> nviPeriods) {
        return new NviPeriodsResponse(nviPeriods.stream().map(NviPeriod::toDto).toList());
    }
}
