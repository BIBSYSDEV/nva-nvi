package no.sikt.nva.nvi.rest.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.rest.upsert.NviApprovalStatus;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CandidateResponse(UUID id, URI publicationId, List<ApprovalStatus> approvalStatuses, List<Note> notes,
                                PeriodStatusDto periodStatus) {

    public static CandidateResponse fromCandidate(Candidate candidate) {
        return CandidateResponse.builder()
                   .withId(candidate.identifier())
                   .withPublicationId(candidate.candidate().publicationId())
                   .withApprovalStatuses(mapToApprovalStatus(candidate))
                   .withNotes(mapToNotes(candidate.notes()))
                   .withPeriodStatus(PeriodStatusDto.fromPeriodStatus(candidate.periodStatus()))
                   .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<Note> mapToNotes(List<DbNote> dbNotes) {
        return dbNotes.stream().map(CandidateResponse::mapToNote).toList();
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

    public static final class Builder {

        private UUID id;
        private URI publicationId;
        private List<ApprovalStatus> approvalStatuses = new ArrayList<>();
        private List<Note> notes = new ArrayList<>();
        private PeriodStatusDto periodStatus;

        private Builder() {
        }

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withApprovalStatuses(List<ApprovalStatus> approvalStatuses) {
            this.approvalStatuses = approvalStatuses;
            return this;
        }

        public Builder withNotes(List<Note> notes) {
            this.notes = notes;
            return this;
        }

        public Builder withPeriodStatus(PeriodStatusDto periodStatus) {
            this.periodStatus = periodStatus;
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(id, publicationId, approvalStatuses, notes, periodStatus);
        }
    }
}
