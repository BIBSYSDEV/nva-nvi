package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.dto.PublicationDetailsDto.fromPublicationDto;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.CandidateAndPeriods;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.NviOrganization;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluatorService {

  private static final String NVI_CANDIDATE_MESSAGE =
      "Evaluated publication with id {} as NviCandidate.";
  private static final String NON_NVI_CANDIDATE_MESSAGE =
      "Evaluated publication with id {} as NonNviCandidate.";
  private static final String SKIPPED_EVALUATION_MESSAGE =
      "Skipping evaluation of publication with id {}.";
  private static final String MALFORMED_DATE_MESSAGE =
      "Skipping evaluation due to invalid year format {}.";
  private static final String REPORTED_CANDIDATE_MESSAGE =
      "Publication is already reported and cannot be updated.";
  private final Logger logger = LoggerFactory.getLogger(EvaluatorService.class);
  private final CreatorVerificationUtil creatorVerificationUtil;
  private final CandidateService candidateService;
  private final PublicationLoaderService publicationLoader;

  public EvaluatorService(
      StorageReader<URI> storageReader,
      CreatorVerificationUtil creatorVerificationUtil,
      CandidateService candidateService) {
    this.creatorVerificationUtil = creatorVerificationUtil;
    this.candidateService = candidateService;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  public Optional<CandidateEvaluatedMessage> evaluateCandidacy(URI publicationBucketUri) {
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    logger.info("Evaluating publication with ID: {}", publication.id());

    var candidateAndPeriods = candidateService.findCandidateAndPeriodsByPublicationId(publication.id());
    if (shouldSkipEvaluation(candidateAndPeriods, publication)) {
      logger.info(SKIPPED_EVALUATION_MESSAGE, publication.id());
      return Optional.empty();
    }

    // Check that the publication meets the basic requirements to be a candidate
    if (isNonCandidate(publication)) {
      return createNonNviCandidateMessage(publication.id());
    }

    // Check that the publication has NVI creators
    var creators = creatorVerificationUtil.getNviCreatorsWithNviInstitutions(publication);
    if (creators.isEmpty()) {
      logger.info("Publication has no NVI creators");
      return createNonNviCandidateMessage(publication.id());
    }

    // Check that the publication can be a candidate in the target period
    if (!canEvaluateInPeriod(candidateAndPeriods, publication.publicationDate())) {
      logger.info("Publication is not applicable in the target period");
      return createNonNviCandidateMessage(publication.id());
    }

    var nviCandidate = constructNviCandidate(publication, publicationBucketUri, creators);
    return createNviCandidateMessage(nviCandidate);
  }

  private boolean shouldSkipEvaluation(
    CandidateAndPeriods candidateAndPeriods, PublicationDto publication) {
    if (hasInvalidPublicationYear(publication)) {
      logger.warn(MALFORMED_DATE_MESSAGE, publication.publicationDate());
      return true;
    }

    if (candidateAndPeriods.getCandidate().map(Candidate::isReported).orElse(false)) {
      logger.warn(REPORTED_CANDIDATE_MESSAGE);
      return true;
    }
    return false;
  }

  private boolean isNonCandidate(PublicationDto publication) {
    try {
      publication.validate();
    } catch (ValidationException e) {
      logger.info("Publication failed validation due to missing required data: {}", e.getMessage());
      return true;
    }

    if (!isPublished(publication)) {
      logger.info("Publication status is not 'published': {}", publication.status());
      return true;
    }

    if (!publication.isApplicable()) {
      logger.info("Publication is not applicable");
      return true;
    }

    return false;
  }

  private boolean isPublished(PublicationDto publication) {
    return nonNull(publication.status()) && "published".equalsIgnoreCase(publication.status());
  }

  private boolean canEvaluateInPeriod(
    CandidateAndPeriods candidateAndPeriods, PublicationDateDto publicationDate) {
    var optionalPeriod = candidateAndPeriods.getPeriod(publicationDate.year());
    if (optionalPeriod.isEmpty()) {
      return false;
    }
    var period = optionalPeriod.get();

    var candidateExistsInPeriod =
        candidateAndPeriods
            .getCandidate()
            .map(candidate -> isApplicableInPeriod(period, candidate))
            .orElse(false);
    return period.isOpen() || candidateExistsInPeriod;
  }

  private boolean isApplicableInPeriod(NviPeriod targetPeriod, Candidate candidate) {
    var hasSamePeriod =
        candidate.getPeriod().filter(period -> targetPeriod.id().equals(period.id())).isPresent();
    return candidate.isApplicable() && hasSamePeriod;
  }

  private UpsertNviCandidateRequest constructNviCandidate(
      PublicationDto publicationDto, URI publicationBucketUri, Collection<NviCreator> creators) {
    var nviCreatorsAsDto = creators.stream().map(NviCreator::toDto).toList();
    var pointCalculation = PointService.calculatePoints(publicationDto, creators);
    var publicationDetails = fromPublicationDto(publicationDto);
    var topLevelNviOrganizations = getTopLevelNviOrganizations(publicationDto, creators);

    return UpsertNviCandidateRequest.builder()
        .withPublicationBucketUri(publicationBucketUri)
        .withPointCalculation(pointCalculation)
        .withPublicationDetails(publicationDetails)
        .withNviCreators(nviCreatorsAsDto)
        .withTopLevelNviOrganizations(topLevelNviOrganizations)
        .build();
  }

  private static List<Organization> getTopLevelNviOrganizations(
      PublicationDto publicationDto, Collection<NviCreator> creators) {
    var topLevelNviAffiliations =
        creators.stream()
            .map(NviCreator::nviAffiliations)
            .flatMap(List::stream)
            .map(NviOrganization::topLevelOrganization)
            .map(NviOrganization::id)
            .collect(Collectors.toSet());
    return publicationDto.topLevelOrganizations().stream()
        .filter(isOneOf(topLevelNviAffiliations))
        .toList();
  }

  private static Predicate<Organization> isOneOf(Collection<URI> organizationIds) {
    return organization -> organizationIds.contains(organization.id());
  }

  private boolean hasInvalidPublicationYear(PublicationDto publication) {
    if (isNull(publication.publicationDate()) || isNull(publication.publicationDate().year())) {
      return true;
    }
    return attempt(() -> Year.parse(publication.publicationDate().year())).isFailure();
  }

  private Optional<CandidateEvaluatedMessage> createNonNviCandidateMessage(URI publicationId) {
    logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationId);
    var nonCandidate = new UpsertNonNviCandidateRequest(publicationId);
    return Optional.of(CandidateEvaluatedMessage.builder().withCandidateType(nonCandidate).build());
  }

  private Optional<CandidateEvaluatedMessage> createNviCandidateMessage(
      UpsertNviCandidateRequest nviCandidate) {
    logger.info(NVI_CANDIDATE_MESSAGE, nviCandidate.publicationId());
    return Optional.of(CandidateEvaluatedMessage.builder().withCandidateType(nviCandidate).build());
  }
}
