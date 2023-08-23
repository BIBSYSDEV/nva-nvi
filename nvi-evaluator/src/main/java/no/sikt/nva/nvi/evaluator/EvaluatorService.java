package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.CandidateStatus;
import no.sikt.nva.nvi.common.model.events.NonNviCandidate;
import no.sikt.nva.nvi.common.model.events.NviCandidate;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.evaluator.calculator.PointCalculator;
import no.unit.nva.events.models.EventReference;

public class EvaluatorService {

    private final StorageReader<EventReference> storageReader;
    private final CandidateCalculator candidateCalculator;

    public EvaluatorService(StorageReader<EventReference> storageReader, CandidateCalculator candidateCalculator) {
        this.storageReader = storageReader;
        this.candidateCalculator = candidateCalculator;
    }

    public CandidateEvaluatedMessage evaluateCandidacy(EventReference input)
        throws JsonProcessingException {
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
        return PointCalculator.calculatePoints(jsonNode, extractApprovalInstitutions(candidateType));
    }

    private static Set<URI> extractApprovalInstitutions(NviCandidate candidate) {
        return candidate.candidateDetails()
                   .verifiedCreators()
                   .stream()
                   .flatMap(creator -> creator.nviInstitutions().stream())
                   .collect(Collectors.toSet());
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
        return attempt(() -> dtoObjectMapper.readTree(content)).map(json -> json.at("/body")).orElseThrow();
    }
}
