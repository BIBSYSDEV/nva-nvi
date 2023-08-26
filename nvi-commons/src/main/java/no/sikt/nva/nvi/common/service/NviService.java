package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.model.events.CandidateStatus.CANDIDATE;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import nva.commons.core.JacocoGenerated;

public class NviService {
    private final NviCandidateRepository nviCandidateRepository;

    public NviService(NviCandidateRepository nviCandidateRepository) {
        this.nviCandidateRepository = nviCandidateRepository;
    }

    //TODO: Remove JacocoGenerated when other if/else cases are implemented
    @JacocoGenerated
    public Optional<CandidateWithIdentifier> upsertCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        if (isNotExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            return Optional.of(createCandidate(evaluatedCandidate));
        }
        return Optional.empty();
    }

    private static List<Creator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .map(verifiedCreatorDto -> new Creator(
                       verifiedCreatorDto.id(), verifiedCreatorDto.nviInstitutions()))
                   .toList();
    }

    private static List<URI> extractInstitutionIds(CandidateDetails candidateDetails) {
        return candidateDetails.verifiedCreators()
                   .stream()
                   .flatMap(creator -> creator.nviInstitutions().stream())
                   .distinct()
                   .toList();
    }

    private static boolean isNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return CANDIDATE.equals(evaluatedCandidate.status());
    }

    private static PublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(),
                                   publicationDate.month(),
                                   publicationDate.day());
    }

    private static List<ApprovalStatus> generatePendingApprovalStatuses(List<URI> institutionUri) {
        return institutionUri.stream().map(uri -> new ApprovalStatus.Builder()
                                                      .withStatus(Status.PENDING)
                                                      .withInstitutionId(uri)
                                                      .build()).toList();
    }

    @JacocoGenerated
    private boolean exists(UUID uuid) {
        return nviCandidateRepository.findById(uuid).isPresent();
    }

    private boolean existsByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private CandidateWithIdentifier createCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        var pendingCandidate = toPendingCandidate(evaluatedCandidate.candidateDetails());
        return nviCandidateRepository.save(pendingCandidate);
    }

    @JacocoGenerated //TODO: Remove when its actually used
    public Optional<CandidateWithIdentifier> findById(UUID uuid) {
        return nviCandidateRepository.findById(uuid);
    }

    @JacocoGenerated //TODO: Remove when its actually used
    public Optional<CandidateWithIdentifier> findByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId);
    }

    private static Candidate toPendingCandidate(CandidateDetails candidateDetails) {
        return new Candidate.Builder()
                   .withPublicationId(candidateDetails.publicationId())
                   .withIsApplicable(true)
                   .withCreators(mapToVerifiedCreators(candidateDetails.verifiedCreators()))
                   .withLevel(Level.parse(candidateDetails.level()))
                   .withInstanceType(candidateDetails.instanceType())
                   .withPublicationDate(mapToPublicationDate(candidateDetails.publicationDate()))
                   .withApprovalStatuses(generatePendingApprovalStatuses(extractInstitutionIds(candidateDetails)))
                   .build();
    }

    //TODO: Remove JacocoGenerated when case for existing candidate is implemented
    @JacocoGenerated
    private boolean isNotExistingCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return !existsByPublicationId(evaluatedCandidate.candidateDetails().publicationId());
    }
}
