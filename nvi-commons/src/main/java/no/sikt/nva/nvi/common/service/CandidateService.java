package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.service.model.Candidate.getApprovalsToDelete;
import static no.sikt.nva.nvi.common.service.model.Candidate.getUpdatedInstitutionPoints;
import static no.sikt.nva.nvi.common.service.model.Candidate.mapToCandidate;
import static no.sikt.nva.nvi.common.service.model.Candidate.mapToNewApprovalDetails;
import static no.sikt.nva.nvi.common.service.model.Candidate.mapToResetApprovals;
import static no.sikt.nva.nvi.common.service.model.Candidate.shouldResetCandidate;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.model.ResponseContext;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.UnusedPrivateField")
public class CandidateService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateService.class);
  private static final PeriodStatus PERIOD_STATUS_NO_PERIOD =
      PeriodStatus.builder().withStatus(PeriodStatus.Status.NO_PERIOD).build();

  private final Environment environment;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;

  public CandidateService(
      Environment environment,
      PeriodRepository periodRepository,
      CandidateRepository candidateRepository) {
    this.environment = environment;
    this.periodRepository = periodRepository;
    this.candidateRepository = candidateRepository;
  }

  @JacocoGenerated
  public static CandidateService defaultCandidateService() {
    var dynamoClient = defaultDynamoClient();
    return new CandidateService(
        new Environment(),
        new PeriodRepository(dynamoClient),
        new CandidateRepository(dynamoClient));
  }

  public Candidate create(Candidate candidate) {
    throw new UnsupportedOperationException();
  }

  public void upsert(UpsertNviCandidateRequest request) {
    var responseContext = candidateRepository.getCandidateAggregate(request.publicationId());
    if (responseContext.candidateAggregate().isPresent()) {
      var originalCandidate = fromAggregate(responseContext);
      updateCandidate(request, originalCandidate);
    } else {
      createCandidate(request);
    }
  }

  private void createCandidate(UpsertNviCandidateRequest request) {
    request.validate();
    candidateRepository.create(
        mapToCandidate(request),
        mapToNewApprovalDetails(request.pointCalculation().institutionPoints()));
  }

  public void updateCandidate(UpsertNviCandidateRequest request, Candidate candidate) {
    request.validate();
    if (candidate.isReported()) {
      throw new IllegalCandidateUpdateException("Can not update reported candidate");
    }

    var updatedCandidate = candidate.apply(request);
    if (shouldResetCandidate(request, candidate) || !candidate.isApplicable()) {
      LOGGER.info("Resetting all approvals for candidate {}", candidate.getIdentifier());
      var approvalsToReset =
          mapToResetApprovals(candidate, updatedCandidate.getInstitutionPoints());
      var approvalsToDelete = getApprovalsToDelete(candidate, updatedCandidate);

      candidateRepository.updateCandidateAndApprovals(
          updatedCandidate.toDao(), approvalsToReset, approvalsToDelete);
    } else {
      var updatedPoints = getUpdatedInstitutionPoints(candidate, updatedCandidate);
      var approvalsToReset = mapToResetApprovals(candidate, updatedPoints);
      LOGGER.info(
          "Resetting individual approvals for candidate {}: {}",
          candidate.getIdentifier(),
          approvalsToReset);
      candidateRepository.updateCandidateAndApprovals(
          updatedCandidate.toDao(), approvalsToReset, emptyList());
    }
  }

  public Optional<Candidate> updateNonCandidate(UpsertNonNviCandidateRequest request) {
    var responseContext = candidateRepository.getCandidateAggregate(request.publicationId());
    LOGGER.info(
        "Updating candidate for publicationId={} to non-candidate", request.publicationId());
    if (responseContext.candidateAggregate().isPresent()) {
      var candidate = fromAggregate(responseContext);
      LOGGER.info("Removing all approvals for candidateId={}", candidate.getIdentifier());
      return Optional.of(updateToNotApplicable(candidate));
    }
    LOGGER.error("No candidate found for publicationId={}", request.publicationId());
    return Optional.empty();
  }

  public Candidate fetch(UUID candidateIdentifier) {
    var responseContext = candidateRepository.getCandidateAggregate(candidateIdentifier);
    return fromAggregate(responseContext);
  }

  public Candidate fetchByPublicationId(URI publicationId) {
    var responseContext = candidateRepository.getCandidateAggregate(publicationId);
    return fromAggregate(responseContext);
  }

  private static PeriodStatus findPeriodStatusFromCached(
      Collection<NviPeriodDao> allPeriods, String year) {
    if (isNull(year)) {
      return PERIOD_STATUS_NO_PERIOD;
    }
    return allPeriods.stream()
        .map(NviPeriodDao::nviPeriod)
        .filter(period -> year.equals(period.publishingYear()))
        .map(PeriodStatus::fromPeriod)
        .findFirst()
        .orElse(PERIOD_STATUS_NO_PERIOD);
  }

  private static CandidateDao updateCandidateToNonApplicable(CandidateDao candidateDao) {
    return candidateDao
        .copy()
        .candidate(candidateDao.candidate().copy().applicable(false).build())
        .periodYear(null)
        .build();
  }

  private Candidate fromAggregate(ResponseContext responseContext) {
    var aggregate =
        responseContext.candidateAggregate().orElseThrow(CandidateNotFoundException::new);
    var periodStatus =
        findPeriodStatusFromCached(
            responseContext.allPeriods(), aggregate.candidate().getPeriodYear());
    return new Candidate(
        candidateRepository,
        aggregate.candidate(),
        aggregate.approvals(),
        aggregate.notes(),
        periodStatus);
  }

  private Candidate updateToNotApplicable(Candidate currentCandidate) {
    var existingCandidateDao = currentCandidate.toDao();
    var nonApplicableCandidate = updateCandidateToNonApplicable(existingCandidateDao);
    var approvalsToDelete =
        currentCandidate.getApprovals().values().stream().map(Approval::toDao).toList();

    candidateRepository.updateCandidateAndApprovals(
        nonApplicableCandidate, emptyList(), approvalsToDelete);

    return new Candidate(
        candidateRepository,
        nonApplicableCandidate,
        emptyList(),
        emptyList(),
        PeriodStatus.builder().withStatus(PeriodStatus.Status.NO_PERIOD).build());
  }
}
