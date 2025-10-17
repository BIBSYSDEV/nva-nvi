package no.sikt.nva.nvi.rest.upsert;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.utils.RequestUtil.getUsername;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest.Builder;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class UpdateNviPeriodHandler
    extends ApiGatewayHandler<UpsertNviPeriodRequest, NviPeriodDto> {

  private final NviPeriodService periodService;

  @JacocoGenerated
  public UpdateNviPeriodHandler() {
    this(new PeriodRepository(defaultDynamoClient()), new Environment());
  }

  public UpdateNviPeriodHandler(PeriodRepository periodRepository, Environment environment) {
    super(UpsertNviPeriodRequest.class, environment);
    this.periodService = new NviPeriodService(environment, periodRepository);
  }

  @Override
  protected void validateRequest(
      UpsertNviPeriodRequest input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI);
  }

  @Override
  protected NviPeriodDto processInput(
      UpsertNviPeriodRequest input, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    return attempt(input::toUpdatePeriodRequest)
        .map(builder -> builder.withModifiedBy(getUsername(requestInfo)))
        .map(Builder::build)
        .map(this::updateAndFetchPeriod)
        .orElseThrow(ExceptionMapper::map);
  }

  private NviPeriodDto updateAndFetchPeriod(UpdatePeriodRequest request) {
    periodService.update(request);
    var publishingYear = String.valueOf(request.publishingYear());
    return periodService.getByPublishingYear(publishingYear).toDto();
  }

  @Override
  protected Integer getSuccessStatusCode(UpsertNviPeriodRequest input, NviPeriodDto output) {
    return HttpURLConnection.HTTP_OK;
  }
}
