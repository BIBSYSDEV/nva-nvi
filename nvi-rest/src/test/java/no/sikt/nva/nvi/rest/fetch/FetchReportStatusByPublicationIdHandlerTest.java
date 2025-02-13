package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
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
import no.sikt.nva.nvi.common.LocalDynamoTestSetup;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.rest.fetch.ReportStatusDto.StatusDto;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FetchReportStatusByPublicationIdHandlerTest extends LocalDynamoTestSetup {

  private static final String PATH_PARAM_PUBLICATION_ID = "publicationId";
  private Context context;
  private ByteArrayOutputStream output;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;

  @BeforeEach
  public void setUp() {
    output = new ByteArrayOutputStream();
    context = new FakeContext();
    localDynamo = initializeTestDatabase();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
  }

  @Test
  void shouldReturnReportedYearWhenPublicationIsReportedInClosedPeriod() throws IOException {
    var dao = setupReportedCandidate(candidateRepository, String.valueOf(CURRENT_YEAR));
    var reportedCandidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
    periodRepository = periodRepositoryReturningClosedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    var candidate = upsert(upsertCandidateRequest);
    candidate.updateApproval(
        new UpdateStatusRequest(institution1, approvalStatus, randomString(), randomString()));
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    var institution1 = randomUri();
    var institution2 = randomUri();
    var upsertCandidateRequest = createUpsertCandidateRequest(institution1, institution2);
    var candidate = upsert(upsertCandidateRequest);
    candidate.updateApproval(
        new UpdateStatusRequest(
            institution1, ApprovalStatus.APPROVED, randomString(), randomString()));
    candidate.updateApproval(
        new UpdateStatusRequest(
            institution2, ApprovalStatus.APPROVED, randomString(), randomString()));
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    var institution1 = randomUri();
    var institution2 = randomUri();
    var upsertCandidateRequest = createUpsertCandidateRequest(institution1, institution2);
    var candidate = upsert(upsertCandidateRequest);
    candidate.updateApproval(
        new UpdateStatusRequest(
            institution1, ApprovalStatus.REJECTED, randomString(), randomString()));
    candidate.updateApproval(
        new UpdateStatusRequest(
            institution2, ApprovalStatus.REJECTED, randomString(), randomString()));
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    periodRepository = periodRepositoryReturningClosedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    Candidate.updateNonCandidate(pendingCandidate::getPublicationId, candidateRepository);
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
    var handler =
        new FetchReportStatusByPublicationIdHandler(candidateRepository, periodRepository);

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
            .withPublicationDate(new PublicationDate(String.valueOf(year), null, null))
            .build();
    return upsert(request);
  }

  private Candidate upsert(UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }
}
