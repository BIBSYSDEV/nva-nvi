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
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;
import no.sikt.nva.nvi.events.evaluator.model.InstitutionPoints;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.CandidateType;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreator;
import no.sikt.nva.nvi.events.model.PublicationDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluatorService {

    private static final String NON_NVI_CANDIDATE_MESSAGE = "Evaluated publication with id {} as NonNviCandidate.";
    private final Logger logger = LoggerFactory.getLogger(EvaluatorService.class);
    private final StorageReader<URI> storageReader;
    private final CreatorVerificationUtil creatorVerificationUtil;
    private final PointService pointService;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final NviPeriodService nviPeriodService;

    public EvaluatorService(StorageReader<URI> storageReader, CreatorVerificationUtil creatorVerificationUtil,
                            PointService pointService, CandidateRepository candidateRepository,
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
            logger.info("Skipping evaluation due to invalid year format {}. Publication id {}",
                        publicationDate.year(), publicationId);
            return Optional.empty();
        }
        if (isPublishedBeforeOrInLatestClosedPeriod(publicationDate)) {
            logger.info("Skipping evaluation. Publication with id {} is published before or same as latest closed "
                        + "period {}",
                        publicationId, publicationDate.year());
            return Optional.empty();
        }
        if (existsAsCandidateInClosedPeriod(publicationId)) {
            logger.info("Skipping evaluation. Publication with id {} already exists as candidate in closed period.",
                        publicationId);
            return Optional.empty();
        }
        if (doesNotMeetNviRequirements(publication)) {
            logger.info(NON_NVI_CANDIDATE_MESSAGE, publicationId);
            return createNonNviMessage(publicationId);
        }
        var verifiedCreatorsWithNviInstitutions =
            creatorVerificationUtil.getVerifiedCreatorsWithNviInstitutionsIfExists(
                publication);
        if (!verifiedCreatorsWithNviInstitutions.isEmpty()) {
            var pointCalculation = pointService.calculatePoints(publication, verifiedCreatorsWithNviInstitutions);
            var nviCandidate = constructNviCandidate(verifiedCreatorsWithNviInstitutions,
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

    private static NviCandidate constructNviCandidate(List<VerifiedNviCreator> verifiedCreatorsWithNviInstitutions,
                                                      PointCalculation pointCalculation, URI publicationId,
                                                      URI publicationBucketUri, PublicationDate date) {
        return NviCandidate.builder()
                   .withPublicationId(publicationId)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withDate(date)
                   .withInstanceType(pointCalculation.instanceType().getValue())
                   .withBasePoints(pointCalculation.basePoints())
                   .withPublicationChannelId(pointCalculation.publicationChannelId())
                   .withChannelType(pointCalculation.channelType().getValue())
                   .withLevel(pointCalculation.level().getValue())
                   .withIsInternationalCollaboration(pointCalculation.isInternationalCollaboration())
                   .withCollaborationFactor(pointCalculation.collaborationFactor())
                   .withCreatorShareCount(pointCalculation.creatorShareCount())
                   .withInstitutionPoints(mapToInstitutionPoints(pointCalculation.institutionPoints()))
                   .withVerifiedCreators(mapToNviCreators(verifiedCreatorsWithNviInstitutions))
                   .withTotalPoints(pointCalculation.totalPoints())
                   .build();
    }

    private static List<NviCreator> mapToNviCreators(List<VerifiedNviCreator> nviCreators) {
        return nviCreators.stream()
                   .map(NviCreator::from)
                   .collect(Collectors.toList());
    }

    private static List<no.sikt.nva.nvi.common.service.model.InstitutionPoints> mapToInstitutionPoints(
        List<InstitutionPoints> institutionPoints) {
        return institutionPoints.stream()
                   .map(InstitutionPoints::toInstitutionPoints)
                   .collect(Collectors.toList());
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
        return publishedYear.isBefore(latestClosedPeriodYear) || publishedYear.equals(latestClosedPeriodYear);
    }

    private boolean hasInvalidPublicationYear(PublicationDate publicationDate) {
        return attempt(() -> Year.parse(publicationDate.year())).isFailure();
    }

    private boolean isPublishedBeforeOrInLatestClosedPeriod(PublicationDate publicationDate) {
        var publishedYear = Year.parse(publicationDate.year());
        return nviPeriodService.fetchLatestClosedPeriodYear()
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
            var existingCandidate = Candidate.fetchByPublicationId(() -> publicationId, candidateRepository,
                                                                   periodRepository);
            return Status.CLOSED_PERIOD.equals(existingCandidate.getPeriod().status());
        } catch (CandidateNotFoundException notFoundException) {
            return false;
        }
    }

    private CandidateEvaluatedMessage constructMessage(CandidateType candidateType) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(candidateType)
                   .build();
    }

    private JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content)).map(json -> json.at(JSON_PTR_BODY)).orElseThrow();
    }
}
