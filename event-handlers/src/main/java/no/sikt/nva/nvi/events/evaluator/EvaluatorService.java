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
import java.net.URI;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator.NviOrganization;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.CandidateType;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate.NviCreator;
import no.sikt.nva.nvi.events.model.NviCandidate.PublicationDate;

public class EvaluatorService {

    private final StorageReader<URI> storageReader;
    private final CandidateCalculator candidateCalculator;
    private final PointCalculator pointCalculator;

    public EvaluatorService(StorageReader<URI> storageReader, CandidateCalculator candidateCalculator,
                            PointCalculator pointCalculator) {
        this.storageReader = storageReader;
        this.candidateCalculator = candidateCalculator;
        this.pointCalculator = pointCalculator;
    }

    public CandidateEvaluatedMessage evaluateCandidacy(URI publicationBucketUri) {
        var publication = extractBodyFromContent(storageReader.read(publicationBucketUri));
        var publicationId = extractPublicationId(publication);
        var verifiedCreatorsWithNviInstitutions = candidateCalculator.getVerifiedCreatorsWithNviInstitutionsIfExists(
            publication);
        if (!verifiedCreatorsWithNviInstitutions.isEmpty()) {
            var pointCalculation = pointCalculator.calculatePoints(publication, verifiedCreatorsWithNviInstitutions);
            var nviCandidate = constructNviCandidate(publication, verifiedCreatorsWithNviInstitutions, pointCalculation,
                                                     publicationId, publicationBucketUri);
            return constructMessage(nviCandidate);
        } else {
            return constructMessage(new NonNviCandidate(publicationId));
        }
    }

    private static NviCandidate constructNviCandidate(JsonNode jsonNode,
                                                      List<VerifiedNviCreator> nviCreators,
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
                   .withInstitutionPoints(pointCalculation.institutionPoints())
                   .withVerifiedCreators(mapToCreators(nviCreators))
                   .withTotalPoints(pointCalculation.totalPoints())
                   .build();
    }

    private static List<NviCreator> mapToCreators(List<VerifiedNviCreator> nviCreators) {
        return nviCreators.stream()
                   .map(EvaluatorService::toNviCreator)
                   .toList();
    }

    private static NviCreator toNviCreator(VerifiedNviCreator creator) {
        return new NviCreator(creator.id(), creator.nviAffiliations()
                                                .stream()
                                                .map(NviOrganization::id)
                                                .toList());
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
