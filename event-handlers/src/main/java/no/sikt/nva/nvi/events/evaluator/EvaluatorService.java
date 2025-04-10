package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.service.model.NviPeriod.fetchByPublishingYear;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_BODY;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getUnverifiedCreators;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getVerifiedCreators;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
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
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// FIXME: Temporary suppression while refactoring, should be removed.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.AvoidLiteralsInIfCondition"})
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
  private final StorageReader<URI> storageReader;
  private final CreatorVerificationUtil creatorVerificationUtil;
  private final PointService pointService;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final PublicationLoaderService publicationLoader;

  public EvaluatorService(
      StorageReader<URI> storageReader,
      CreatorVerificationUtil creatorVerificationUtil,
      PointService pointService,
      CandidateRepository candidateRepository,
      PeriodRepository periodRepository) {
    this.storageReader = storageReader;
    this.creatorVerificationUtil = creatorVerificationUtil;
    this.pointService = pointService;
    this.candidateRepository = candidateRepository;
    this.periodRepository = periodRepository;
    this.publicationLoader = new PublicationLoaderService(storageReader);
  }

  public Optional<CandidateEvaluatedMessage> evaluateCandidacy(URI publicationBucketUri) {
    var publication = extractBodyFromContent(storageReader.read(publicationBucketUri));
    // TODO: Running extraction here to verify that it works, but we do not fully use it yet.
    // TODO: Replace use of JsonNode with the extracted Publication object.
    var publicationDto = publicationLoader.extractAndTransform(publicationBucketUri);
    logger.info("Publication: {}", publicationDto.id());

    var candidate = fetchOptionalCandidate(publicationDto.id()).orElse(null);
    var period = fetchOptionalPeriod(publicationDto.publicationDate().year()).orElse(null);

    // Check if the publication can be evaluated
    if (shouldSkipEvaluation(candidate, publicationDto.publicationDate())) {
      logger.info(SKIPPED_EVALUATION_MESSAGE, publicationDto.id());
      return Optional.empty();
    }

    // Check if the publication meets the requirements to be a candidate
    if (!publicationDto.isApplicable()) {
      logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationDto.id());
      return createNonNviCandidateMessage(publicationDto.id());
    }

    var creators = creatorVerificationUtil.getNviCreatorsWithNviInstitutions(publicationDto);
    if (creators.isEmpty()) {
      logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationDto.id());
      return createNonNviCandidateMessage(publicationDto.id());
    }

    // Check that the publication can be a candidate in the target period
    var periodExists = nonNull(period) && nonNull(period.getId());
    var periodIsOpen = periodExists && !period.isClosed();
    var candidateExistsInPeriod =
        periodExists && nonNull(candidate) && isApplicableInPeriod(period, candidate);
    var canEvaluateInPeriod = periodIsOpen || candidateExistsInPeriod;
    if (!canEvaluateInPeriod) {
      logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationDto.id());
      return createNonNviCandidateMessage(publicationDto.id());
    }

    logger.info(NVI_CANDIDATE_MESSAGE, publicationDto.id());
    var nviCandidate =
        constructNviCandidate(publication, publicationDto, publicationBucketUri, creators);
    return createNviCandidateMessage(nviCandidate);
  }

  private boolean shouldSkipEvaluation(Candidate candidate, PublicationDateDto publicationDate) {
    if (hasInvalidPublicationYear(publicationDate)) {
      logger.warn(MALFORMED_DATE_MESSAGE, publicationDate.year());
      return true;
    }

    if (nonNull(candidate) && candidate.isReported()) {
      logger.warn(REPORTED_CANDIDATE_MESSAGE);
      return true;
    }
    return false;
  }

  private boolean isApplicableInPeriod(NviPeriod targetPeriod, Candidate candidate) {
    var candidatePeriod = candidate.getPeriod();
    if (isNull(candidatePeriod) || isNull(candidatePeriod.id())) {
      return false;
    }
    var hasSamePeriod = candidatePeriod.id().equals(targetPeriod.getId());
    return candidate.isApplicable() && hasSamePeriod;
  }

  private NviCandidate constructNviCandidate(
      JsonNode publication,
      PublicationDto publicationDto,
      URI publicationBucketUri,
      Collection<NviCreator> creators) {
    var verifiedCreatorsWithNviInstitutions = getVerifiedCreators(creators);
    var unverifiedCreatorsWithNviInstitutions = getUnverifiedCreators(creators);
    var pointCalculation =
        pointService.calculatePoints(
            publication,
            verifiedCreatorsWithNviInstitutions,
            unverifiedCreatorsWithNviInstitutions);

    return NviCandidate.builder()
        .withPublicationId(publicationDto.id())
        .withPublicationBucketUri(publicationBucketUri)
        .withDate(publicationDto.publicationDate())
        .withInstanceType(publicationDto.publicationType())
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

  private boolean hasInvalidPublicationYear(PublicationDateDto publicationDate) {
    return attempt(() -> Year.parse(publicationDate.year())).isFailure();
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
    var nonCandidate = new NonNviCandidate(publicationId);
    return Optional.of(CandidateEvaluatedMessage.builder().withCandidateType(nonCandidate).build());
  }

  private Optional<CandidateEvaluatedMessage> createNviCandidateMessage(NviCandidate nviCandidate) {
    return Optional.of(CandidateEvaluatedMessage.builder().withCandidateType(nviCandidate).build());
  }

  private JsonNode extractBodyFromContent(String content) {
    try {
      return dtoObjectMapper.readTree(content).at(JSON_PTR_BODY);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
