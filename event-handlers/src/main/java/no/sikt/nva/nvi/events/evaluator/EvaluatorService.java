package no.sikt.nva.nvi.events.evaluator;

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
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.model.InstitutionPoints;
import no.sikt.nva.nvi.events.evaluator.model.InstitutionPoints.InstitutionAffiliationPoints;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.CandidateType;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreatorWithAffiliationPoints;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreatorWithAffiliationPoints.AffiliationPoints;
import no.sikt.nva.nvi.events.model.NviCandidate.PublicationDate;

public class EvaluatorService {

    private final StorageReader<URI> storageReader;
    private final CandidateCalculator candidateCalculator;
    private final PointService pointService;

    public EvaluatorService(StorageReader<URI> storageReader, CandidateCalculator candidateCalculator,
                            PointService pointService) {
        this.storageReader = storageReader;
        this.candidateCalculator = candidateCalculator;
        this.pointService = pointService;
    }

    public CandidateEvaluatedMessage evaluateCandidacy(URI publicationBucketUri) {
        var publication = extractBodyFromContent(storageReader.read(publicationBucketUri));
        var publicationId = extractPublicationId(publication);
        var verifiedCreatorsWithNviInstitutions = candidateCalculator.getVerifiedCreatorsWithNviInstitutionsIfExists(
            publication);
        if (!verifiedCreatorsWithNviInstitutions.isEmpty()) {
            var pointCalculation = pointService.calculatePoints(publication, verifiedCreatorsWithNviInstitutions);
            var nviCandidate = constructNviCandidate(publication, pointCalculation, publicationId,
                                                     publicationBucketUri);
            return constructMessage(nviCandidate);
        } else {
            return constructMessage(new NonNviCandidate(publicationId));
        }
    }

    private static NviCandidate constructNviCandidate(JsonNode jsonNode,
                                                      PointCalculation pointCalculation, URI publicationId,
                                                      URI publicationBucketUri) {
        return NviCandidate.builder()
                   .withPublicationId(publicationId)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withDate(extractPublicationDate(jsonNode))
                   .withInstanceType(pointCalculation.instanceType().getValue())
                   .withBasePoints(pointCalculation.basePoints())
                   .withPublicationChannelId(pointCalculation.publicationChannelId())
                   .withChannelType(pointCalculation.channelType().getValue())
                   .withLevel(pointCalculation.level().getValue())
                   .withIsInternationalCollaboration(pointCalculation.isInternationalCollaboration())
                   .withCollaborationFactor(pointCalculation.collaborationFactor())
                   .withCreatorShareCount(pointCalculation.creatorShareCount())
                   .withInstitutionPoints(mapToInstitutionPoints(pointCalculation.institutionPoints()))
                   .withVerifiedCreators(mapToNviCreatorWithAffiliationPoints(pointCalculation))
                   .withTotalPoints(pointCalculation.totalPoints())
                   .build();
    }

    private static Map<URI, BigDecimal> mapToInstitutionPoints(List<InstitutionPoints> institutionPoints) {
        return institutionPoints.stream().collect(Collectors.toMap(InstitutionPoints::institutionId,
                                                                   InstitutionPoints::institutionPoints));
    }

    private static List<NviCreatorWithAffiliationPoints> mapToNviCreatorWithAffiliationPoints(
        PointCalculation pointCalculation) {
        return pointCalculation.institutionPoints().stream()
                   .flatMap(institutionPoints -> institutionPoints.institutionAffiliationPoints().stream())
                   .collect(Collectors.groupingBy(InstitutionAffiliationPoints::nviCreator))
                   .entrySet()
                   .stream()
                   .map(entry -> new NviCreatorWithAffiliationPoints(entry.getKey(), mapToAffiliationPoints(entry)))
                   .toList();
    }

    private static List<NviCreatorWithAffiliationPoints.AffiliationPoints> mapToAffiliationPoints(
        Entry<URI, List<InstitutionAffiliationPoints>> entry) {
        return entry.getValue().stream()
                   .map(AffiliationPoints::from)
                   .toList();
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

    private CandidateEvaluatedMessage constructMessage(CandidateType candidateType) {
        return CandidateEvaluatedMessage.builder()
                   .withCandidateType(candidateType)
                   .build();
    }

    private JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content)).map(json -> json.at(JSON_PTR_BODY)).orElseThrow();
    }
}
