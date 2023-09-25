package no.sikt.nva.nvi;

import static nva.commons.core.paths.UriWrapper.HTTPS;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.rest.fetch.ApprovalStatus;
import no.sikt.nva.nvi.rest.fetch.Note;
import no.sikt.nva.nvi.rest.upsert.NviApprovalStatus;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;

public class CandidateResponseMapper {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private static final String CANDIDATE_PATH = "candidate";

    private CandidateResponseMapper() {
    }

    public static CandidateResponse fromCandidate(Candidate candidate) {
        return CandidateResponse.builder()
                   .withIdentifier(getIdentifier(candidate))
                   .withPublicationId(candidate.candidate().publicationId())
                   .withApprovalStatuses(CandidateResponseMapper.mapToApprovalStatus(candidate))
                   .withNotes(CandidateResponseMapper.mapToNotes(candidate.notes()))
                   .build();
    }

    private static URI getIdentifier(Candidate candidate) {
        return new UriWrapper(HTTPS, API_DOMAIN)
                   .addChild(BASE_PATH, CANDIDATE_PATH, candidate.identifier().toString())
                   .getUri();
    }

    private static List<Note> mapToNotes(List<DbNote> dbNotes) {
        return dbNotes.stream().map(CandidateResponseMapper::mapToNote).toList();
    }

    private static Note mapToNote(DbNote dbNote) {
        return new Note(dbNote.user().getValue(), dbNote.text(), dbNote.createdDate());
    }

    private static List<ApprovalStatus> mapToApprovalStatus(Candidate candidate) {
        return candidate.approvalStatuses()
                   .stream()
                   .map(approvalStatus -> mapToApprovalStatus(approvalStatus, candidate.candidate().points()))
                   .toList();
    }

    private static ApprovalStatus mapToApprovalStatus(DbApprovalStatus approvalStatus,
                                                      List<DbInstitutionPoints> institutionPoints) {
        return ApprovalStatus.builder()
                   .withInstitutionId(approvalStatus.institutionId())
                   .withStatus(NviApprovalStatus.parse(approvalStatus.status().getValue()))
                   .withPoints(getPointsForApprovalStatus(institutionPoints, approvalStatus))
                   .withAssignee(approvalStatus.assignee())
                   .withFinalizedBy(approvalStatus.finalizedBy())
                   .withFinalizedDate(approvalStatus.finalizedDate())
                   .build();
    }

    private static BigDecimal getPointsForApprovalStatus(List<DbInstitutionPoints> points,
                                                         DbApprovalStatus approvalStatus) {
        return points.stream()
                   .filter(institutionPoints -> isForSameInstitution(approvalStatus, institutionPoints))
                   .map(DbInstitutionPoints::points)
                   .findFirst()
                   .orElse(null);
    }

    private static boolean isForSameInstitution(DbApprovalStatus approvalStatus,
                                                DbInstitutionPoints institutionPoints) {
        return institutionPoints.institutionId().equals(approvalStatus.institutionId());
    }
}
