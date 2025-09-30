package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getUpdateNviPeriodHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createAdminUserInstance;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.rest.model.UpsertNviPeriodRequest;
import no.sikt.nva.nvi.rest.upsert.UpdateNviPeriodHandler;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class UpdateNviPeriodHandlerTest {

  private static final Context CONTEXT = mock(Context.class);
  private static final URI ORGANIZATION = randomOrganizationId();
  private static final UserInstance ADMIN_USER = createAdminUserInstance(ORGANIZATION);
  private ByteArrayOutputStream output;
  private UpdateNviPeriodHandler handler;
  private TestScenario scenario;
  private NviPeriodService periodService;

  @BeforeEach
  void init() {
    scenario = new TestScenario();
    output = new ByteArrayOutputStream();

    var environment = getUpdateNviPeriodHandlerEnvironment();
    periodService = new NviPeriodService(environment, scenario.getPeriodRepository());
    handler = new UpdateNviPeriodHandler(scenario.getPeriodRepository(), environment);
  }

  @Test
  void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
    var year = String.valueOf(CURRENT_YEAR);
    var persistedPeriod = setupOpenPeriod(scenario, year);
    var updateRequest = updateRequest(year, persistedPeriod);
    var otherUser = createCuratorUserInstance(ORGANIZATION);

    handleRequest(updateRequest, otherUser);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldReturnNotFoundWhenPeriodDoesNotExists() throws IOException {
    handleRequest(randomUpsertNviPeriodRequest(), ADMIN_USER);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
  }

  @Test
  void shouldUpdateNviPeriodSuccessfully() {
    var year = String.valueOf(CURRENT_YEAR);
    var persistedPeriod = setupFuturePeriod(scenario, year);
    var updateRequest = updateRequest(year, persistedPeriod);
    var otherUser = createAdminUserInstance(randomOrganizationId());

    handleRequest(updateRequest, otherUser);

    var updatedPeriod = periodService.fetchByPublishingYear(year);
    assertThat(updatedPeriod.reportingDate()).isNotEqualTo(persistedPeriod.reportingDate());
    assertThat(updatedPeriod.modifiedBy()).isEqualTo(otherUser.userName());
  }

  @Test
  void shouldUpdateExistingRecordOnUpdate() {
    var year = String.valueOf(CURRENT_YEAR);
    var persistedPeriod = setupFuturePeriod(scenario, year);
    var updateRequest = updateRequest(year, persistedPeriod);

    handleRequest(updateRequest, ADMIN_USER);

    var allPeriods = scenario.getPeriodRepository().getPeriods();
    assertThat(allPeriods.size()).isOne();
  }

  private UpsertNviPeriodRequest updateRequest(String year, NviPeriod persistedPeriod) {
    return new UpsertNviPeriodRequest(
        year,
        persistedPeriod.startDate().plus(1, ChronoUnit.DAYS).toString(),
        persistedPeriod.reportingDate().plus(1, ChronoUnit.DAYS).toString());
  }

  private UpsertNviPeriodRequest randomUpsertNviPeriodRequest() {
    return new UpsertNviPeriodRequest(
        String.valueOf(CURRENT_YEAR + 1),
        ZonedDateTime.now().plusMonths(1).toInstant().toString(),
        ZonedDateTime.now().plusMonths(10).toInstant().toString());
  }

  private void handleRequest(UpsertNviPeriodRequest request, UserInstance userInstance) {
    try {
      var input = toInputStream(request, userInstance);
      handler.handleRequest(input, output, CONTEXT);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream toInputStream(UpsertNviPeriodRequest request, UserInstance userInstance)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<UpsertNviPeriodRequest>(JsonUtils.dtoObjectMapper)
        .withBody(request)
        .withAccessRights(
            userInstance.topLevelOrganizationId(),
            userInstance.accessRights().toArray(AccessRight[]::new))
        .withUserName(userInstance.userName().toString())
        .build();
  }
}
