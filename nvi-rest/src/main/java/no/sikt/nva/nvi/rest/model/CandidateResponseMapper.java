package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.paths.UriWrapper.HTTPS;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.model.ApprovalStatusDao.ApprovalStatusData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstitutionPoints;
import no.sikt.nva.nvi.common.db.model.NoteDao.NoteData;
import no.sikt.nva.nvi.common.service.Candidate;
import no.sikt.nva.nvi.rest.upsert.NviApprovalStatus;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public final class CandidateResponseMapper {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private static final String CANDIDATE_PATH = "candidate";

    private CandidateResponseMapper() {
    }

    public static CandidateResponse fromCandidate(Candidate candidate) {
        return CandidateResponse.builder()
                   .withId(constructId(candidate))
                   .withIdentifier(candidate.identifier())
                   .withPublicationId(candidate.candidate().publicationId())
                   .withApprovalStatuses(CandidateResponseMapper.mapToApprovalStatus(candidate))
                   .withNotes(CandidateResponseMapper.mapToNotes(candidate.notes()))
                   .withPeriodStatus(PeriodStatus.fromPeriodStatus(candidate.periodStatus()))
                   .build();
    }

    private static URI constructId(Candidate candidate) {
        return new UriWrapper(HTTPS, API_DOMAIN)
                   .addChild(BASE_PATH, CANDIDATE_PATH, candidate.identifier().toString())
                   .getUri();
    }

    private static List<Note> mapToNotes(List<NoteData> noteData) {
        return noteData.stream().map(CandidateResponseMapper::mapToNote).toList();
    }

    private static Note mapToNote(NoteData noteData) {
        return new Note(noteData.user().value(), noteData.text(), noteData.createdDate());
    }

    private static List<ApprovalStatus> mapToApprovalStatus(Candidate candidate) {
        return candidate.approvalStatuses()
                   .stream()
                   .map(approvalStatus -> mapToApprovalStatus(approvalStatus, candidate.candidate().points()))
                   .toList();
    }

    private static ApprovalStatus mapToApprovalStatus(ApprovalStatusData approvalStatus,
                                                      List<InstitutionPoints> institutionPoints) {
        return ApprovalStatus.builder()
                   .withInstitutionId(approvalStatus.institutionId())
                   .withStatus(NviApprovalStatus.parse(approvalStatus.status().getValue()))
                   .withPoints(getPointsForApprovalStatus(institutionPoints, approvalStatus))
                   .withAssignee(approvalStatus.assignee())
                   .withFinalizedBy(approvalStatus.finalizedBy())
                   .withFinalizedDate(approvalStatus.finalizedDate())
                   .withReason(approvalStatus.reason())
                   .build();
    }

    private static BigDecimal getPointsForApprovalStatus(List<InstitutionPoints> points,
                                                         ApprovalStatusData approvalStatus) {
        return points.stream()
                   .filter(institutionPoints -> isForSameInstitution(approvalStatus, institutionPoints))
                   .map(InstitutionPoints::points)
                   .findFirst()
                   .orElse(null);
    }

    private static boolean isForSameInstitution(ApprovalStatusData approvalStatus,
                                                InstitutionPoints institutionPoints) {
        return institutionPoints.institutionId().equals(approvalStatus.institutionId());
    }
}
