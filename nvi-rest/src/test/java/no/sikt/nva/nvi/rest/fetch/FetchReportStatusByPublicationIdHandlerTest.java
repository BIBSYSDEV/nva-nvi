package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.rest.fetch.ReportStatusDto.StatusDto;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FetchReportStatusByPublicationIdHandlerTest {
  private static final Environment ENVIRONMENT = new Environment();
  private static final String PATH_PARAM_PUBLICATION_ID = "publicationId";
  private Context context;
  private ByteArrayOutputStream output;
  private CandidateRepository candidateRepository;
  private CandidateService candidateService;
  private TestScenario scenario;
  private FetchReportStatusByPublicationIdHandler handler;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    setupOpenPeriod(scenario, CURRENT_YEAR);
    candidateRepository = scenario.getCandidateRepository();
    candidateService = scenario.getCandidateService();
    output = new ByteArrayOutputStream();
    context = new FakeContext();
    handler = new FetchReportStatusByPublicationIdHandler(candidateService, ENVIRONMENT);
  }

  @Test
  void shouldReturnReportedYearWhenPublicationIsReportedInClosedPeriod() throws IOException {
    var dao = setupReportedCandidate(candidateRepository, String.valueOf(CURRENT_YEAR));
    var reportedCandidate = candidateService.getByIdentifier(dao.identifier());
    setupClosedPeriod(scenario, CURRENT_YEAR);

    handler.handleRequest(createRequest(reportedCandidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(reportedCandidate.getPublicationId())
            .withStatus(StatusDto.REPORTED)
            .withYear(String.valueOf(CURRENT_YEAR))
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @Test
  void shouldReturnPendingReviewWhenPublicationIsCandidateWithOnlyPendingApprovalsInOpenPeriod()
      throws IOException {
    var pendingCandidate = setupCandidateWithPublicationYear(CURRENT_YEAR);

    handler.handleRequest(createRequest(pendingCandidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(pendingCandidate.getPublicationId())
            .withStatus(StatusDto.PENDING_REVIEW)
            .withYear(String.valueOf(CURRENT_YEAR))
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @ParameterizedTest(
      name = "Should return under review when at least one approval with status: {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void
      shouldReturnUnderReviewWhenPublicationIsCandidateWithAtLeastOneNonPendingApprovalInOpenPeriod(
          ApprovalStatus approvalStatus) throws IOException {
    var institution1 = randomUri();
    var involvedInstitutions = new URI[] {institution1, randomUri()};
    var upsertCandidateRequest = createUpsertCandidateRequest(involvedInstitutions).build();
    var candidate = scenario.upsertCandidate(upsertCandidateRequest);
    scenario.updateApprovalStatus(candidate.getIdentifier(), approvalStatus, institution1);

    handler.handleRequest(createRequest(candidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(candidate.getPublicationId())
            .withStatus(StatusDto.UNDER_REVIEW)
            .withYear(String.valueOf(CURRENT_YEAR))
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @Test
  void shouldReturnApprovedWhenPublicationIsCandidateWithAllApprovalsApproved() throws IOException {
    var organization1 = randomTopLevelOrganization();
    var organization2 = randomTopLevelOrganization();
    var request = createUpsertCandidateRequest(organization1, organization2).build();
    var candidate = scenario.upsertCandidate(request);
    scenario.updateApprovalStatus(
        candidate.getIdentifier(), ApprovalStatus.APPROVED, organization1.id());
    scenario.updateApprovalStatus(
        candidate.getIdentifier(), ApprovalStatus.APPROVED, organization2.id());

    handler.handleRequest(createRequest(candidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(candidate.getPublicationId())
            .withStatus(StatusDto.APPROVED)
            .withYear(String.valueOf(CURRENT_YEAR))
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @Test
  void shouldReturnRejectedWhenPublicationIsCandidateWithAllApprovalsRejected() throws IOException {
    var organization1 = randomTopLevelOrganization();
    var organization2 = randomTopLevelOrganization();
    var request = createUpsertCandidateRequest(organization1, organization2).build();
    var candidate = scenario.upsertCandidate(request);
    scenario.updateApprovalStatus(
        candidate.getIdentifier(), ApprovalStatus.REJECTED, organization1.id());
    scenario.updateApprovalStatus(
        candidate.getIdentifier(), ApprovalStatus.REJECTED, organization2.id());

    handler.handleRequest(createRequest(candidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(candidate.getPublicationId())
            .withStatus(StatusDto.REJECTED)
            .withYear(String.valueOf(CURRENT_YEAR))
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @Test
  void shouldReturnNotReportedWhenPublicationIsCandidateIsNotReportedInClosedPeriod()
      throws IOException {
    var pendingCandidate = setupCandidateWithPublicationYear(CURRENT_YEAR);
    setupClosedPeriod(scenario, CURRENT_YEAR);

    handler.handleRequest(createRequest(pendingCandidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(pendingCandidate.getPublicationId())
            .withStatus(StatusDto.NOT_REPORTED)
            .withYear(String.valueOf(CURRENT_YEAR))
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @Test
  void shouldReturnNotCandidateWhenPublicationIsNotApplicableCandidate() throws IOException {
    var pendingCandidate = setupCandidateWithPublicationYear(CURRENT_YEAR);
    var upsertRequest = createUpsertNonCandidateRequest(pendingCandidate.getPublicationId());
    candidateService.updateNonCandidate(upsertRequest);

    handler.handleRequest(createRequest(pendingCandidate.getPublicationId()), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(pendingCandidate.getPublicationId())
            .withStatus(StatusDto.NOT_CANDIDATE)
            .build();
    assertEquals(expected, actualResponseBody);
  }

  @Test
  void shouldReturnNotCandidateWhenPublicationIsNotFound() throws IOException {
    var notFoundPublicationId = randomUri();

    handler.handleRequest(createRequest(notFoundPublicationId), output, context);

    var actualResponseBody =
        GatewayResponse.fromOutputStream(output, ReportStatusDto.class)
            .getBodyObject(ReportStatusDto.class);
    var expected =
        ReportStatusDto.builder()
            .withPublicationId(notFoundPublicationId)
            .withStatus(StatusDto.NOT_CANDIDATE)
            .build();
    assertEquals(expected, actualResponseBody);
  }

  private static InputStream createRequest(URI publicationId) throws JsonProcessingException {
    return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
        .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
        .withPathParameters(Map.of(PATH_PARAM_PUBLICATION_ID, publicationId.toString()))
        .build();
  }

  private Candidate setupCandidateWithPublicationYear(int year) {
    var request =
        UpsertRequestBuilder.randomUpsertRequestBuilder()
            .withPublicationDate(new PublicationDateDto(String.valueOf(year), null, null))
            .build();
    return scenario.upsertCandidate(request);
  }
}
