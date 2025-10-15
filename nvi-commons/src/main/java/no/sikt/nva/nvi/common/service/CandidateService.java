package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.service.NviPeriodService.findByPublishingYear;
import static no.sikt.nva.nvi.common.service.model.Candidate.getApprovalsToDelete;
import static no.sikt.nva.nvi.common.service.model.Candidate.getUpdatedInstitutionPoints;
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
import no.sikt.nva.nvi.common.db.model.CandidateAggregate;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.CandidatePermissions;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.CandidateContext;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class CandidateService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateService.class);
  private final Environment environment;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final NviPeriodService periodService;

  public CandidateService(
      Environment environment,
      PeriodRepository periodRepository,
      CandidateRepository candidateRepository) {
    this.environment = environment;
    this.periodRepository = periodRepository;
    this.candidateRepository = candidateRepository;
    this.periodService = new NviPeriodService(environment, periodRepository);
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
    LOGGER.info("Upserting candidate for publicationId={}", request.publicationId());
    request.validate();
    var candidateContext = getCandidateContext(request.publicationId());
    var targetPeriod =
        findByPublishingYear(candidateContext.allPeriods(), request.publicationYear())
            .orElseThrow();

    candidateContext
        .candidate()
        .ifPresentOrElse(
            existingCandidate -> updateCandidate(request, existingCandidate, targetPeriod),
            () -> createCandidate(request, targetPeriod));
  }

  public void update(Candidate candidate) {
    LOGGER.info("Saving updated candidate: {}", candidate.identifier());
    candidateRepository.updateCandidateAndApprovals(candidate.toDao(), emptyList(), emptyList());
  }

  private void createCandidate(UpsertNviCandidateRequest request, NviPeriod period) {
    LOGGER.info("Creating new candidate for publicationId={}", request.publicationId());

    var identifier = randomUUID();
    var candidate = Candidate.fromRequest(identifier, request, period, environment);
    var approvals = candidate.getApprovals().values().stream().map(Approval::toDao).toList();

    candidateRepository.create(candidate.toDao(), approvals);
  }

  private void updateCandidate(
      UpsertNviCandidateRequest request, Candidate candidate, NviPeriod targetPeriod) {
    var updatedCandidate = candidate.apply(request, targetPeriod);
    if (shouldResetCandidate(request, candidate) || !candidate.isApplicable()) {
      LOGGER.info("Resetting all approvals for candidate {}", candidate.identifier());
      var institutionsToReset = updatedCandidate.getInstitutionPoints();
      var approvalsToReset =
          candidate.createResetApprovalsForInstitutions(institutionsToReset).stream()
              .map(Approval::toDao)
              .toList();
      var approvalsToDelete = getApprovalsToDelete(candidate, updatedCandidate);

      candidateRepository.updateCandidateAndApprovals(
          updatedCandidate.toDao(), approvalsToReset, approvalsToDelete);
    } else {
      var institutionsToReset = getUpdatedInstitutionPoints(candidate, updatedCandidate);
      var approvalsToReset =
          candidate.createResetApprovalsForInstitutions(institutionsToReset).stream()
              .map(Approval::toDao)
              .toList();
      LOGGER.info(
          "Resetting individual approvals for candidate {}: {}",
          candidate.identifier(),
          approvalsToReset);
      candidateRepository.updateCandidateAndApprovals(
          updatedCandidate.toDao(), approvalsToReset, emptyList());
    }
  }

  public void updateNonCandidate(UpsertNonNviCandidateRequest request) {
    var publicationId = request.publicationId();
    LOGGER.info("Updating candidate for publicationId={} to non-candidate", publicationId);
    var candidateContext = getCandidateContext(publicationId);

    if (candidateContext.candidate().isEmpty()) {
      LOGGER.warn("No candidate found for publicationId={}", publicationId);
    } else {
      var candidate = candidateContext.candidate().orElseThrow();
      var nonApplicableCandidate = updateCandidateToNonApplicable(candidate.toDao());
      var approvalsToDelete =
          candidate.getApprovals().values().stream().map(Approval::toDao).toList();

      candidateRepository.updateCandidateAndApprovals(
          nonApplicableCandidate, emptyList(), approvalsToDelete);
      LOGGER.info("Successfully updated publicationId={} to non-candidate", publicationId);
    }
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
      return new CandidateContext(Optional.empty(), periodService.getAll());
    }
    return findAggregate(optionalCandidate.get().identifier());
  }

  public void updateApprovalAssignee(Candidate candidate, UpdateAssigneeRequest request) {
    LOGGER.info("Updating assignee for candidateId={}: {}", candidate.identifier(), request);
    var updatedApproval = candidate.updateApprovalAssignee(request);
    candidateRepository.updateCandidateItems(
        candidate.toDao(), List.of(updatedApproval.toDao()), emptyList());
  }

  public void updateApprovalStatus(
      Candidate candidate, UpdateStatusRequest request, UserInstance user) {
    LOGGER.info("Updating approval status for candidateId={}: {}", candidate.identifier(), request);
    var currentStatus = candidate.getApprovalStatus(request.institutionId());
    if (request.approvalStatus().equals(currentStatus)) {
      LOGGER.warn("Approval status update attempted with no change in status: {}", request);
      return;
    }

    var permissions = new CandidatePermissions(candidate, user);
    var attemptedOperation = CandidateOperation.fromApprovalStatus(request.approvalStatus());
    try {
      permissions.validateAuthorization(attemptedOperation);
    } catch (UnauthorizedException e) {
      throw new IllegalStateException("Cannot update approval status");
    }

    var updatedApproval = candidate.updateApprovalStatus(request);
    LOGGER.info("Saving updated approval with status {}", updatedApproval.status());
    candidateRepository.updateCandidateItems(
        candidate.toDao(), List.of(updatedApproval.toDao()), emptyList());
  }

  public void createNote(Candidate candidate, CreateNoteRequest request) {
    LOGGER.info("Creating note for candidateId={}", candidate.identifier());
    var updatedItems = candidate.createNote(request);
    var approvalsToUpdate =
        updatedItems.updatedApproval().map(Approval::toDao).map(List::of).orElse(emptyList());
    var notesToAdd = List.of(updatedItems.note().toDao());

    candidateRepository.updateCandidateItems(candidate.toDao(), approvalsToUpdate, notesToAdd);
  }

  public void deleteNote(Candidate candidate, DeleteNoteRequest request) {
    var noteToDelete = candidate.deleteNote(request);
    candidateRepository.deleteNote(candidate.identifier(), noteToDelete.noteIdentifier());
  }

  private CandidateContext findAggregate(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate and periods by identifier {}", candidateIdentifier);

    var candidateFuture = candidateRepository.getCandidateAggregateAsync(candidateIdentifier);
    var periodsFuture = periodRepository.getPeriodsAsync();

    return candidateFuture.thenCombine(periodsFuture, this::mapToCandidateContext).join();
  }

  private CandidateContext mapToCandidateContext(
      List<Dao> candidateItems, List<NviPeriodDao> allPeriods) {
    var candidateAggregate = CandidateAggregate.fromQueryResponse(candidateItems);
    var periods = allPeriods.stream().map(NviPeriod::fromDao).toList();

    var candidate = candidateAggregate.map(aggregate -> fromAggregate(aggregate, periods));
    return new CandidateContext(candidate, periods);
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
    var period = findByPublishingYear(allPeriods, publicationYear);
    return Candidate.fromDao(
        candidateAggregate.candidate(),
        candidateAggregate.approvals(),
        candidateAggregate.notes(),
        period,
        environment);
  }
}
