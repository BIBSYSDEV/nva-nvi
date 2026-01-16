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
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.CandidateAggregate;
import no.sikt.nva.nvi.common.db.model.TableScanRequest;
import no.sikt.nva.nvi.common.db.model.YearQueryRequest;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.CandidateAndPeriods;
import no.sikt.nva.nvi.common.service.model.Note;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CandidateService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateService.class);
  private final Environment environment;
  private final CandidateRepository candidateRepository;
  private final NviPeriodService periodService;

  public CandidateService(
      Environment environment,
      PeriodRepository periodRepository,
      CandidateRepository candidateRepository) {
    this.environment = environment;
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

  public void upsertCandidate(UpsertNviCandidateRequest request) {
    LOGGER.info("Upserting candidate for publicationId={}", request.publicationId());
    request.validate();
    var candidateContext = findCandidateAndPeriodsByPublicationId(request.publicationId());
    var targetPeriod =
        findByPublishingYear(candidateContext.allPeriods(), request.publicationYear())
            .orElseThrow(PeriodNotFoundException.forYear(request.publicationYear()));

    candidateContext
        .getCandidate()
        .ifPresentOrElse(
            existingCandidate -> updateCandidate(request, existingCandidate, targetPeriod),
            () -> createCandidate(request, targetPeriod));
  }

  private void createCandidate(UpsertNviCandidateRequest request, NviPeriod period) {
    LOGGER.info("Creating new candidate for publicationId={}", request.publicationId());

    var identifier = randomUUID();
    var candidate = Candidate.fromRequest(identifier, request, period, environment);
    var approvals = candidate.approvals().values().stream().map(Approval::toDao).toList();

    candidateRepository.create(candidate.toDao(), approvals);
  }

  public void refreshCandidate(UUID candidateIdentifier) {
    LOGGER.info("Refreshing persisted data for candidateIdentifier={}", candidateIdentifier);
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    updateCandidate(candidate);
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

      candidateRepository.updateCandidateAggregate(
          updatedCandidate.toDao(), approvalsToReset, approvalsToDelete, emptyList());
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
      candidateRepository.updateCandidateAggregate(
          updatedCandidate.toDao(), approvalsToReset, emptyList(), emptyList());
    }
  }

  public void updateCandidate(Candidate candidate) {
    LOGGER.info("Saving candidate aggregate for publicationId={}", candidate.getPublicationId());
    var approvals = candidate.approvals().values().stream().map(Approval::toDao).toList();
    var notes = candidate.notes().values().stream().map(Note::toDao).toList();
    candidateRepository.updateCandidateAggregate(candidate.toDao(), approvals, emptyList(), notes);
  }

  public void updateCandidate(UpsertNonNviCandidateRequest request) {
    var publicationId = request.publicationId();
    LOGGER.info("Updating candidate for publicationId={} to non-candidate", publicationId);
    var candidateContext = findCandidateAndPeriodsByPublicationId(publicationId);
    var optionalCandidate = candidateContext.getCandidate();

    if (optionalCandidate.isEmpty()) {
      LOGGER.warn("No candidate found for publicationId={}", publicationId);
    } else {
      var candidate = optionalCandidate.get();
      var updatedCandidate = candidate.updateToNonCandidate();
      var approvalsToDelete = candidate.approvals().values().stream().map(Approval::toDao).toList();

      candidateRepository.updateCandidateAggregate(
          updatedCandidate.toDao(), emptyList(), approvalsToDelete, emptyList());
      LOGGER.info("Successfully updated publicationId={} to non-candidate", publicationId);
    }
  }

  public Candidate getCandidateByIdentifier(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate by identifier {}", candidateIdentifier);
    var responseContext = findCandidateAndPeriodsByIdentifier(candidateIdentifier);
    return responseContext.getCandidate().orElseThrow(CandidateNotFoundException::new);
  }

  public Candidate getCandidateByPublicationId(URI publicationId) {
    LOGGER.info("Fetching candidate by publication id {}", publicationId);
    var responseContext = findCandidateAndPeriodsByPublicationId(publicationId);
    return responseContext.getCandidate().orElseThrow(CandidateNotFoundException::new);
  }

  public CandidateAndPeriods findCandidateAndPeriodsByPublicationId(URI publicationId) {
    LOGGER.info("Fetching candidate and periods by publication id {}", publicationId);
    var candidateIdentifier = candidateRepository.findByPublicationId(publicationId);

    if (candidateIdentifier.isEmpty()) {
      LOGGER.info("No candidate found for publicationId={}", publicationId);
      return new CandidateAndPeriods(null, periodService.getAll());
    }
    return findCandidateAndPeriodsByIdentifier(candidateIdentifier.get());
  }

  public ListingResult<UUID> listCandidateIdentifiers(
      int segment, int totalSegments, int pageSize, Map<String, String> startKey) {
    return candidateRepository.scanForCandidateIdentifiers(
        segment, totalSegments, pageSize, startKey);
  }

  public ListingResult<UUID> listCandidateIdentifiersByYear(
      String year, int pageSize, Map<String, String> startKey) {
    return candidateRepository.scanForCandidateIdentifiers(year, pageSize, startKey);
  }

  public ListingResult<UUID> listCandidateIdentifiers(TableScanRequest request) {
    return candidateRepository.scanForCandidateIdentifiers(request);
  }

  public ListingResult<UUID> listCandidateIdentifiersByYear(
    YearQueryRequest request) {
    return candidateRepository.scanForCandidateIdentifiers(request);
  }

  private CandidateAndPeriods findCandidateAndPeriodsByIdentifier(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate and periods by identifier {}", candidateIdentifier);
    var candidateFuture = candidateRepository.getCandidateAggregateAsync(candidateIdentifier);
    var periodsFuture = periodService.getAllAsync();

    return candidateFuture.thenCombine(periodsFuture, this::mergeCandidateAndPeriods).join();
  }

  private CandidateAndPeriods mergeCandidateAndPeriods(
      Collection<Dao> candidateItems, Collection<NviPeriod> periods) {
    var candidateAggregate = CandidateAggregate.fromQueryResponse(candidateItems);
    var candidate =
        candidateAggregate
            .map(aggregate -> aggregate.toCandidate(environment, periods))
            .orElse(null);

    return new CandidateAndPeriods(candidate, periods);
  }
}
