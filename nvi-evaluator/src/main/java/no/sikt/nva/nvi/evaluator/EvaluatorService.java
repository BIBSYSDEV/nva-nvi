package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.evaluator.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.evaluator.model.CandidateStatus;
import no.sikt.nva.nvi.evaluator.model.NonNviCandidate;
import no.sikt.nva.nvi.evaluator.model.NviCandidate;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails.Creator;
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
        var jsonNode = extractBodyFromContent(storageReader.read(input));
        var candidateType = candidateCalculator.calculateNviType(jsonNode);
        var publicationBucketUri = input.getUri();
        if (candidateType instanceof NviCandidate) {
            return constructCandidateResponse(publicationBucketUri,
                                              (NviCandidate) candidateType,
                                              calculatePoints(jsonNode, (NviCandidate) candidateType));
        } else {
            return constructNonCandidateResponse(publicationBucketUri, (NonNviCandidate) candidateType);
        }
    }

    private static Map<URI, BigDecimal> calculatePoints(JsonNode jsonNode, NviCandidate candidateType) {
        return PointCalculator.calculatePoints(jsonNode, extractNviCreatorsWithInstitutions(candidateType))
                   .institutionPoints();
    }

    private static Map<URI, List<URI>> extractNviCreatorsWithInstitutions(NviCandidate candidate) {
        return candidate.candidateDetails()
                   .verifiedCreators()
                   .stream()
                   .collect(Collectors.toMap(Creator::id, Creator::nviInstitutions));
    }

    private static CandidateDetails createCandidateDetails(NonNviCandidate candidateType) {
        return new CandidateDetails(candidateType.publicationId(), null, null, null, null);
    }

    private CandidateEvaluatedMessage constructCandidateResponse(URI publicationBucketUri, NviCandidate candidateType,
                                                                 Map<URI, BigDecimal> pointsPerInstitution) {
        return CandidateEvaluatedMessage.builder().withStatus(CandidateStatus.CANDIDATE)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withCandidateDetails(candidateType.candidateDetails())
                   .withInstitutionPoints(pointsPerInstitution)
                   .build();
    }

    private CandidateEvaluatedMessage constructNonCandidateResponse(URI publicationBucketUri,
                                                                    NonNviCandidate candidateType) {
        return CandidateEvaluatedMessage.builder().withStatus(CandidateStatus.NON_CANDIDATE)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withCandidateDetails(createCandidateDetails(candidateType))
                   .build();
    }

    private JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content)).map(json -> json.at(JSON_PTR_BODY)).orElseThrow();
    }
}
