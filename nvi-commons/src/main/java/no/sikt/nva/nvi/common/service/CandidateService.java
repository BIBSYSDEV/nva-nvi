package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.service.model.Candidate.getApprovalsToDelete;
import static no.sikt.nva.nvi.common.service.model.Candidate.getUpdatedInstitutionPoints;
import static no.sikt.nva.nvi.common.service.model.Candidate.mapToCandidate;
import static no.sikt.nva.nvi.common.service.model.Candidate.mapToNewApprovalDetails;
import static no.sikt.nva.nvi.common.service.model.Candidate.mapToResetApprovals;
import static no.sikt.nva.nvi.common.service.model.Candidate.shouldResetCandidate;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.model.CandidateAggregate;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.CandidateContext;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
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

  public void upsert(UpsertNviCandidateRequest request) {
    var responseContext = getCandidateContext(request.publicationId());

    if (responseContext.candidate().isPresent()) {
      var originalCandidate = responseContext.candidate().get();
      updateCandidate(request, originalCandidate);
    } else {
      createCandidate(request);
    }
  }

  public void update(Candidate candidate) {
    LOGGER.info("Saving updated candidate: {}", candidate.getIdentifier());
    var updatedDao =
        candidate.toDao().copy().version(randomUUID().toString()).build(); // FIXME: Duplicate
    candidateRepository.updateCandidateAndApprovals(updatedDao, emptyList(), emptyList());
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
      // TODO: Leave this validation to the Candidate
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

  // FIXME: Return void
  public Optional<Candidate> updateNonCandidate(UpsertNonNviCandidateRequest request) {
    var responseContext = getCandidateContext(request.publicationId());
    LOGGER.info(
        "Updating candidate for publicationId={} to non-candidate", request.publicationId());
    if (responseContext.candidate().isPresent()) {
      var candidate = responseContext.candidate().get();
      LOGGER.info("Removing all approvals for candidateId={}", candidate.getIdentifier());
      return Optional.of(updateToNotApplicable(candidate));
    }
    LOGGER.error("No candidate found for publicationId={}", request.publicationId());
    return Optional.empty();
  }

  public Candidate getByIdentifier(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate by identifier {}", candidateIdentifier);
    var responseContext = findAggregate(candidateIdentifier);
    return responseContext.candidate().orElseThrow(CandidateNotFoundException::new);
  }

  public Candidate getByPublicationId(URI publicationId) {
    LOGGER.info("Fetching candidate by publication id {}", publicationId);
    var responseContext = getCandidateContext(publicationId);
    return responseContext.candidate().orElseThrow(CandidateNotFoundException::new);
  }

  public CandidateContext getCandidateContext(URI publicationId) {
    LOGGER.info("Fetching candidate and periods by publication id {}", publicationId);
    var optionalCandidate = candidateRepository.findByPublicationId(publicationId);

    if (optionalCandidate.isEmpty()) {
      LOGGER.info("No candidate found for publicationId={}", publicationId);
      var allPeriods = periodRepository.getPeriods().stream().map(NviPeriod::fromDao).toList();
      return new CandidateContext(Optional.empty(), allPeriods);
    }
    return findAggregate(optionalCandidate.get().identifier());
  }

  private CandidateContext findAggregate(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate and periods by identifier {}", candidateIdentifier);

    var candidateFuture = candidateRepository.getCandidateAggregateAsync(candidateIdentifier);
    var periodsFuture = periodRepository.getPeriodsAsync();

    return candidateFuture
        .thenCombine(
            periodsFuture,
            (candidateItems, periods) -> mapToCandidateContext(candidateItems, periods))
        .join();
  }

  private CandidateContext mapToCandidateContext(
      List<Dao> candidateItems, List<NviPeriodDao> allPeriods) {
    var candidateAggregate = CandidateAggregate.fromQueryResponse(candidateItems);
    var periods = allPeriods.stream().map(NviPeriod::fromDao).toList();

    var candidate = candidateAggregate.map(aggregate -> fromAggregate(aggregate, periods));
    return new CandidateContext(candidate, periods);
  }

  private static PeriodStatus findPeriodStatus(Collection<NviPeriod> allPeriods, String year) {
    if (isNull(year)) {
      return PERIOD_STATUS_NO_PERIOD;
    }

    // FIXME: Shouldn't need to go via dao
    return allPeriods.stream()
        .filter(period -> period.hasPublishingYear(year))
        .map(NviPeriod::toDao)
        .map(NviPeriodDao::nviPeriod)
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

  private Candidate fromAggregate(
      CandidateAggregate candidateAggregate, Collection<NviPeriod> allPeriods) {
    var publicationYear = candidateAggregate.candidate().getPeriodYear();
    var periodStatus = findPeriodStatus(allPeriods, publicationYear);
    return new Candidate(
        candidateRepository,
        candidateAggregate.candidate(),
        candidateAggregate.approvals(),
        candidateAggregate.notes(),
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
