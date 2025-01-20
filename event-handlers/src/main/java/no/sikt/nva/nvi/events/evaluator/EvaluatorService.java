package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.common.utils.GraphUtils.isNviCandidate;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_BODY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_DAY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MONTH;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getUnverifiedCreators;
import static no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil.getVerifiedCreators;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.CandidateType;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.PublicationDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluatorService {

  private static final String NON_NVI_CANDIDATE_MESSAGE =
      "Evaluated publication with id {} as NonNviCandidate.";
  private final Logger logger = LoggerFactory.getLogger(EvaluatorService.class);
  private final StorageReader<URI> storageReader;
  private final CreatorVerificationUtil creatorVerificationUtil;
  private final PointService pointService;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final NviPeriodService nviPeriodService;

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
    this.nviPeriodService = new NviPeriodService(periodRepository);
  }

  public Optional<CandidateEvaluatedMessage> evaluateCandidacy(URI publicationBucketUri) {
    var publication = extractBodyFromContent(storageReader.read(publicationBucketUri));
    var publicationId = extractPublicationId(publication);
    var publicationDate = extractPublicationDate(publication);
    if (hasInvalidPublicationYear(publicationDate)) {
      logger.warn(
          "Skipping evaluation due to invalid year format {}. Publication id {}",
          publicationDate.year(),
          publicationId);
      return Optional.empty();
    }
    if (isPublishedBeforeOrInLatestClosedPeriod(publicationDate)) {
      logger.info(
          "Skipping evaluation. Publication with id {} is published before or same as latest closed"
              + " period {}",
          publicationId,
          publicationDate.year());
      return Optional.empty();
    }
    if (existsAsCandidateInClosedPeriod(publicationId)) {
      logger.info(
          "Skipping evaluation. Publication with id {} already exists as candidate in closed"
              + " period.",
          publicationId);
      return Optional.empty();
    }
    if (doesNotMeetNviRequirements(publication)) {
      logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationId);
      return createNonNviMessage(publicationId);
    }
    var creators = creatorVerificationUtil.getNviCreatorsWithNviInstitutions(publication);
    var verifiedCreatorsWithNviInstitutions = getVerifiedCreators(creators);
    var unverifiedCreatorsWithNviInstitutions = getUnverifiedCreators(creators);

    if (!creators.isEmpty()) {
      var pointCalculation =
          pointService.calculatePoints(
              publication,
              verifiedCreatorsWithNviInstitutions,
              unverifiedCreatorsWithNviInstitutions);
      var nviCandidate =
          constructNviCandidate(
              verifiedCreatorsWithNviInstitutions,
              unverifiedCreatorsWithNviInstitutions,
              pointCalculation,
              publicationId,
              publicationBucketUri,
              publicationDate);
      logger.info("Evaluated publication with id {} as NviCandidate.", publicationId);
      return Optional.of(constructMessage(nviCandidate));
    } else {
      logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationId);
      return createNonNviMessage(publicationId);
    }
  }

  private static NviCandidate constructNviCandidate(
      List<VerifiedNviCreator> verifiedCreatorsWithNviInstitutions,
      List<UnverifiedNviCreator> unverifiedCreatorsWithNviInstitutions,
      PointCalculation pointCalculation,
      URI publicationId,
      URI publicationBucketUri,
      PublicationDate date) {
    return NviCandidate.builder()
        .withPublicationId(publicationId)
        .withPublicationBucketUri(publicationBucketUri)
        .withDate(date)
        .withInstanceType(pointCalculation.instanceType())
        .withBasePoints(pointCalculation.basePoints())
        .withPublicationChannelId(pointCalculation.publicationChannelId())
        .withChannelType(pointCalculation.channelType().getValue())
        .withLevel(pointCalculation.level().getValue())
        .withIsInternationalCollaboration(pointCalculation.isInternationalCollaboration())
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

  private static URI extractPublicationId(JsonNode publication) {
    return URI.create(extractJsonNodeTextValue(publication, JSON_PTR_ID));
  }

  private static PublicationDate extractPublicationDate(JsonNode publication) {
    return mapToPublicationDate(publication.at(JSON_PTR_PUBLICATION_DATE));
  }

  private static PublicationDate mapToPublicationDate(JsonNode publicationDateNode) {
    var year = publicationDateNode.at(JSON_PTR_YEAR);
    var month = publicationDateNode.at(JSON_PTR_MONTH);
    var day = publicationDateNode.at(JSON_PTR_DAY);

    return Optional.of(new PublicationDate(day.textValue(), month.textValue(), year.textValue()))
        .orElse(new PublicationDate(null, null, year.textValue()));
  }

  private static boolean isBeforeOrEqualTo(Year publishedYear, Year latestClosedPeriodYear) {
    return publishedYear.isBefore(latestClosedPeriodYear)
        || publishedYear.equals(latestClosedPeriodYear);
  }

  private boolean hasInvalidPublicationYear(PublicationDate publicationDate) {
    return attempt(() -> Year.parse(publicationDate.year())).isFailure();
  }

  private boolean isPublishedBeforeOrInLatestClosedPeriod(PublicationDate publicationDate) {
    var publishedYear = Year.parse(publicationDate.year());
    return nviPeriodService
        .fetchLatestClosedPeriodYear()
        .map(Year::of)
        .map(latestClosedPeriodYear -> isBeforeOrEqualTo(publishedYear, latestClosedPeriodYear))
        .orElse(false);
  }

  private Optional<CandidateEvaluatedMessage> createNonNviMessage(URI publicationId) {
    return Optional.of(constructMessage(new NonNviCandidate(publicationId)));
  }

  private boolean doesNotMeetNviRequirements(JsonNode publication) {
    var model = createModel(publication);
    return !isNviCandidate(model);
  }

  private boolean existsAsCandidateInClosedPeriod(URI publicationId) {
    try {
      var existingCandidate =
          Candidate.fetchByPublicationId(
              () -> publicationId, candidateRepository, periodRepository);
      return Status.CLOSED_PERIOD.equals(existingCandidate.getPeriod().status());
    } catch (CandidateNotFoundException notFoundException) {
      return false;
    }
  }

  private CandidateEvaluatedMessage constructMessage(CandidateType candidateType) {
    return CandidateEvaluatedMessage.builder().withCandidateType(candidateType).build();
  }

  private JsonNode extractBodyFromContent(String content) {
    try {
      return dtoObjectMapper.readTree(content).at(JSON_PTR_BODY);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
