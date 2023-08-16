package no.sikt.nva.nvi.common.service;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.dao.ApprovalStatus;
import no.sikt.nva.nvi.common.model.dao.Candidate;
import no.sikt.nva.nvi.common.model.dao.Level;
import no.sikt.nva.nvi.common.model.dao.PublicationDate;
import no.sikt.nva.nvi.common.model.dao.Status;
import no.sikt.nva.nvi.common.model.dao.VerifiedCreator;
import no.sikt.nva.nvi.common.model.dto.CandidateDetailsDto;
import no.sikt.nva.nvi.common.model.dto.EvaluatedCandidateDto;
import no.sikt.nva.nvi.common.model.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.model.dto.VerifiedCreatorDto;
import nva.commons.core.JacocoGenerated;

public class NviService {

    public static final String NVI_CANDIDATE = "Candidate";
    private final NviCandidateRepository nviCandidateRepository;

    public NviService(NviCandidateRepository nviCandidateRepository) {
        this.nviCandidateRepository = nviCandidateRepository;
    }

    //TODO: Remove JacocoGenerated when other if/else cases are implemented
    @JacocoGenerated
    public void upsertCandidate(EvaluatedCandidateDto evaluatedCandidate) {
        if (isNotExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            createCandidate(evaluatedCandidate);
        }
    }

    private static List<VerifiedCreator> mapToVerifiedCreators(List<VerifiedCreatorDto> verifiedCreatorDtos) {
        return verifiedCreatorDtos.stream()
                   .map(verifiedCreatorDto -> new VerifiedCreator(
                       verifiedCreatorDto.id(), verifiedCreatorDto.nviInstitutions()))
                   .toList();
    }

    private static List<URI> extractInstitutionIds(CandidateDetailsDto candidateDetails) {
        return candidateDetails.verifiedCreatorDtos()
                   .stream()
                   .flatMap(creator -> creator.nviInstitutions().stream())
                   .distinct()
                   .toList();
    }

    private static boolean isNviCandidate(EvaluatedCandidateDto evaluatedCandidate) {
        return evaluatedCandidate.type().equals(NVI_CANDIDATE);
    }

    private static PublicationDate mapToPublicationDate(PublicationDateDto publicationDate) {
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

    private boolean exists(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private void createCandidate(EvaluatedCandidateDto evaluatedCandidate) {
        var candidateDetails = evaluatedCandidate.candidateDetailsDto();
        var publicationDate = candidateDetails.publicationDateDto();
        var pendingCandidate = new Candidate.Builder()
                                   .withPublicationId(candidateDetails.publicationId())
                                   .withIsApplicable(true)
                                   .withCreators(mapToVerifiedCreators(candidateDetails.verifiedCreatorDtos()))
                                   .withLevel(Level.parse(candidateDetails.level()))
                                   .withInstanceType(candidateDetails.instanceType())
                                   .withPublicationDate(mapToPublicationDate(publicationDate))
                                   .withApprovalStatuses(
                                       generatePendingApprovalStatuses(extractInstitutionIds(candidateDetails)))
                                   .build();
        nviCandidateRepository.save(pendingCandidate);
    }

    //TODO: Remove JacocoGenerated when case for existing candidate is implemented
    @JacocoGenerated
    private boolean isNotExistingCandidate(EvaluatedCandidateDto evaluatedCandidate) {
        return !exists(evaluatedCandidate.candidateDetailsDto().publicationId());
    }
}
