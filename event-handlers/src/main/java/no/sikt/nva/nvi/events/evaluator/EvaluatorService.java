package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.service.model.NviPeriod.fetchByPublishingYear;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getUnverifiedCreators;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getVerifiedCreators;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.dto.UpsertNonNviCandidateRequest;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.exceptions.ValidationException;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.evaluator.model.NviCreator;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
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
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final PublicationLoaderService publicationLoader;

  public EvaluatorService(
      StorageReader<URI> storageReader,
      CreatorVerificationUtil creatorVerificationUtil,
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository) {
    this.creatorVerificationUtil = creatorVerificationUtil;
    this.candidateRepository = candidateRepository;
    this.periodRepository = periodRepository;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  public Optional<CandidateEvaluatedMessage> evaluateCandidacy(URI publicationBucketUri) {
    var publication = publicationLoader.extractAndTransform(publicationBucketUri);
    logger.info("Evaluating publication with ID: {}", publication.id());

    // Check that the publication can be evaluated
    var candidate = fetchOptionalCandidate(publication.id()).orElse(null);
    if (shouldSkipEvaluation(candidate, publication)) {
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
    var period = fetchOptionalPeriod(publication.publicationDate().year()).orElse(null);
    var periodExists = nonNull(period) && nonNull(period.getId());
    var periodIsOpen = periodExists && !period.isClosed();
    var candidateExistsInPeriod =
        periodExists && nonNull(candidate) && isApplicableInPeriod(period, candidate);
    var canEvaluateInPeriod = periodIsOpen || candidateExistsInPeriod;
    if (!canEvaluateInPeriod) {
      logger.info("Publication is not applicable in the target period");
      return createNonNviCandidateMessage(publication.id());
    }

    var nviCandidate = constructNviCandidate(publication, publicationBucketUri, creators);
    return createNviCandidateMessage(nviCandidate);
  }

  private boolean shouldSkipEvaluation(Candidate candidate, PublicationDto publication) {
    if (hasInvalidPublicationYear(publication)) {
      logger.warn(MALFORMED_DATE_MESSAGE, publication.publicationDate());
      return true;
    }

    if (nonNull(candidate) && candidate.isReported()) {
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

  private boolean isApplicableInPeriod(NviPeriod targetPeriod, Candidate candidate) {
    var candidatePeriod = candidate.getPeriod();
    if (isNull(candidatePeriod) || isNull(candidatePeriod.id())) {
      return false;
    }
    var hasSamePeriod = candidatePeriod.id().equals(targetPeriod.getId());
    return candidate.isApplicable() && hasSamePeriod;
  }

  private UpsertNviCandidateRequest constructNviCandidate(
      PublicationDto publicationDto, URI publicationBucketUri, Collection<NviCreator> creators) {
    var verifiedCreatorsWithNviInstitutions = getVerifiedCreators(creators);
    var unverifiedCreatorsWithNviInstitutions = getUnverifiedCreators(creators);
    var pointCalculation =
        PointService.calculatePoints(
            publicationDto,
            verifiedCreatorsWithNviInstitutions,
            unverifiedCreatorsWithNviInstitutions);

    return UpsertNviCandidateRequest.builder()
        .withPublicationId(publicationDto.id())
        .withPublicationBucketUri(publicationBucketUri)
        .withDate(publicationDto.publicationDate())
        .withInstanceType(publicationDto.publicationType())
        .withAbstract(publicationDto.abstractText())
        .withPageCount(publicationDto.pageCount())
        .withBasePoints(pointCalculation.basePoints())
        .withPublicationChannelId(pointCalculation.publicationChannelId())
        .withChannelType(pointCalculation.channelType().getValue())
        .withLevel(pointCalculation.scientificValue().getValue())
        .withIsInternationalCollaboration(publicationDto.isInternationalCollaboration())
        .withCollaborationFactor(pointCalculation.collaborationFactor())
        .withCreatorShareCount(pointCalculation.creatorShareCount())
        .withInstitutionPoints(pointCalculation.institutionPoints())
        .withVerifiedNviCreators(mapVerifiedCreatorsToDto(verifiedCreatorsWithNviInstitutions))
        .withUnverifiedNviCreators(
            mapUnverifiedCreatorsToDto(unverifiedCreatorsWithNviInstitutions))
        .withTotalPoints(pointCalculation.totalPoints())
        .build();
  }

  private static List<VerifiedNviCreatorDto> mapVerifiedCreatorsToDto(
      List<VerifiedNviCreator> nviCreators) {
    return nviCreators.stream().map(VerifiedNviCreator::toDto).toList();
  }

  private static List<UnverifiedNviCreatorDto> mapUnverifiedCreatorsToDto(
      List<UnverifiedNviCreator> nviCreators) {
    return nviCreators.stream().map(UnverifiedNviCreator::toDto).toList();
  }

  private boolean hasInvalidPublicationYear(PublicationDto publication) {
    if (isNull(publication.publicationDate()) || isNull(publication.publicationDate().year())) {
      return true;
    }
    return attempt(() -> Year.parse(publication.publicationDate().year())).isFailure();
  }

  private Optional<Candidate> fetchOptionalCandidate(URI publicationId) {
    try {
      return Optional.of(
          Candidate.fetchByPublicationId(
              () -> publicationId, candidateRepository, periodRepository));
    } catch (CandidateNotFoundException notFoundException) {
      return Optional.empty();
    }
  }

  private Optional<NviPeriod> fetchOptionalPeriod(String year) {
    try {
      return Optional.of(fetchByPublishingYear(year, periodRepository));
    } catch (PeriodNotFoundException notFoundException) {
      return Optional.empty();
    }
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
