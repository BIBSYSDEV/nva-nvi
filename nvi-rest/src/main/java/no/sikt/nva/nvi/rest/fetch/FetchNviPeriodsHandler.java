package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.List;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.rest.model.NviPeriodsResponse;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class FetchNviPeriodsHandler extends ApiGatewayHandler<Void, NviPeriodsResponse> {

  private final NviPeriodService nviPeriodService;

  @JacocoGenerated
  public FetchNviPeriodsHandler() {
    this(new NviPeriodService(new PeriodRepository(defaultDynamoClient())), new Environment());
  }

  public FetchNviPeriodsHandler(NviPeriodService nviPeriodService, Environment environment) {
    super(Void.class, environment);
    this.nviPeriodService = nviPeriodService;
  }

  @Override
  protected void validateRequest(Void input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI);
  }

  @Override
  protected NviPeriodsResponse processInput(Void input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    return attempt(nviPeriodService::fetchAll).map(this::toNviPeriodsResponse).orElseThrow();
  }

  @Override
  protected Integer getSuccessStatusCode(Void input, NviPeriodsResponse output) {
    return HttpURLConnection.HTTP_OK;
  }

  private NviPeriodsResponse toNviPeriodsResponse(List<NviPeriod> nviPeriods) {
    return new NviPeriodsResponse(nviPeriods.stream().map(NviPeriod::toDto).toList());
  }
}
