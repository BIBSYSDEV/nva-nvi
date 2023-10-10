package no.sikt.nva.nvi.evaluator;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.evaluator.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.evaluator.model.CandidateType;
import no.sikt.nva.nvi.evaluator.model.NonNviCandidate;
import no.sikt.nva.nvi.evaluator.model.NviCandidate;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails.PublicationDate;
import no.sikt.nva.nvi.evaluator.model.PointCalculation;
import no.unit.nva.events.models.EventReference;

public class EvaluatorService {

    public static final String JSON_PTR_BODY = "/body";
    private final StorageReader<EventReference> storageReader;
    private final CandidateCalculator candidateCalculator;

    public EvaluatorService(StorageReader<EventReference> storageReader, CandidateCalculator candidateCalculator) {
        this.storageReader = storageReader;
        this.candidateCalculator = candidateCalculator;
    }

    public CandidateEvaluatedMessage evaluateCandidacy(EventReference input) {
        var publication = extractBodyFromContent(storageReader.read(input));
        var publicationBucketUri = input.getUri();
        var publicationId = extractPublicationId(publication);
        var verifiedCreatorsWithNviInstitutions = candidateCalculator.getVerifiedCreatorsWithNviInstitutionsIfExists(
            publication);
        if (!verifiedCreatorsWithNviInstitutions.isEmpty()) {
            var pointCalculation = calculatePoints(publication, verifiedCreatorsWithNviInstitutions);
            var nviCandidate = constructNviCandidate(publication, verifiedCreatorsWithNviInstitutions, pointCalculation,
                                                     publicationId);
            return constructMessage(publicationBucketUri, nviCandidate);
        }
        return constructMessage(publicationBucketUri, new NonNviCandidate(publicationId));
    }

    private static PointCalculation calculatePoints(JsonNode jsonNode, Map<URI, List<URI>> nviCreators) {
        return PointCalculator.calculatePoints(jsonNode, nviCreators);
    }

    private static NviCandidate constructNviCandidate(JsonNode jsonNode,
                                                      Map<URI, List<URI>> verifiedCreatorsWithNviInstitutions,
                                                      PointCalculation pointCalculation, URI publicationId) {
        return new NviCandidate(CandidateDetails.builder()
                                    .withPublicationId(publicationId)
                                    .withPublicationDate(extractPublicationDate(jsonNode))
                                    .withInstanceType(pointCalculation.instanceType().value())
                                    .withBasePoints(pointCalculation.basePoints())
                                    .withPublicationChannelId(pointCalculation.publicationChannelId())
                                    .withChannelType(pointCalculation.channelType().value())
                                    .withLevel(pointCalculation.level().value())
                                    .withIsInternationalCollaboration(pointCalculation.isInternationalCollaboration())
                                    .withCollaborationFactor(pointCalculation.collaborationFactor())
                                    .withCreatorShareCount(pointCalculation.creatorShareCount())
                                    .withInstitutionPoints(pointCalculation.institutionPoints())
                                    .withVerifiedCreators(mapToCreators(verifiedCreatorsWithNviInstitutions))
                                    .build());
    }

    private static List<Creator> mapToCreators(Map<URI, List<URI>> verifiedCreatorsWithNviInstitutions) {
        return verifiedCreatorsWithNviInstitutions.entrySet()
                   .stream()
                   .map(entry -> new Creator(entry.getKey(),
                                             entry.getValue()))
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

    private CandidateEvaluatedMessage constructMessage(URI publicationBucketUri, CandidateType candidateType) {
        return CandidateEvaluatedMessage.builder()
                   .withPublicationBucketUri(publicationBucketUri)
                   .withCandidateType(candidateType)
                   .build();
    }

    private JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content)).map(json -> json.at(JSON_PTR_BODY)).orElseThrow();
    }
}
