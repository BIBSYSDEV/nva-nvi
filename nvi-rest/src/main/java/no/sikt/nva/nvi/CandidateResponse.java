package no.sikt.nva.nvi;

import static java.util.Objects.nonNull;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.fetch.ApprovalStatus;
import no.sikt.nva.nvi.rest.fetch.InstitutionPoints;
import no.sikt.nva.nvi.rest.fetch.Note;
import no.sikt.nva.nvi.rest.model.ReportStatus;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CandidateResponse(UUID id,
                                URI publicationId,
                                List<ApprovalStatus> approvalStatuses,
                                List<InstitutionPoints> points,
                                List<Note> notes,
                                String reportStatus) {

    public static CandidateResponse fromCandidate(Candidate candidate, NviService service) {
        return CandidateResponse.builder()
                   .withId(candidate.identifier())
                   .withPublicationId(candidate.candidate().publicationId())
                   .withApprovalStatuses(mapToApprovalStatus(candidate))
                   .withPoints(mapToInstitutionPoints(candidate.candidate()))
                   .withNotes(mapToNotes(candidate.notes()))
                   .withReportStatus(getReportStatus(candidate, service))
                   .build();
    }

    private static String getReportStatus(Candidate candidate, NviService service) {
        var period = attempt(() -> service.getPeriod(candidate.candidate().publicationDate().year()))
                         .orElse(failure -> null);
        return nonNull(period) && Instant.now().isBefore(period.reportingDate())
                   ? ReportStatus.REPORTABLE.getValue()
                   : ReportStatus.NOT_REPORTABLE.getValue();
    }

    private static List<Note> mapToNotes(List<DbNote> dbNotes) {
        return dbNotes.stream().map(CandidateResponse::mapToNote).toList();
    }

    private static Note mapToNote(DbNote dbNote) {
        return new Note(dbNote.user(), dbNote.text(), dbNote.createdDate());
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(DbCandidate candidate) {
        return candidate.points()
                   .stream()
                   .map(CandidateResponse::mapToInstitutionPoint)
                   .toList();
    }


    private static InstitutionPoints mapToInstitutionPoint(
        DbInstitutionPoints institutionPoints) {
        return new InstitutionPoints(institutionPoints.institutionId(),
                                     institutionPoints.points());
    }

    public static Builder builder() {
        return new Builder();
    }

    private static List<ApprovalStatus> mapToApprovalStatus(Candidate candidate) {
        return candidate.approvalStatuses()
                   .stream()
                   .map(CandidateResponse::mapToApprovalStatus)
                   .toList();
    }

    private static ApprovalStatus mapToApprovalStatus(
        DbApprovalStatus approvalStatus) {
        return new ApprovalStatus(approvalStatus.institutionId(), approvalStatus.status(),
                                  approvalStatus.assignee(), approvalStatus.finalizedBy(),
                                  approvalStatus.finalizedDate());
    }

    public static final class Builder {

        private UUID id;
        private URI publicationId;
        private List<ApprovalStatus> approvalStatuses = new ArrayList<>();
        private List<InstitutionPoints> points = new ArrayList<>();
        private List<Note> notes = new ArrayList<>();
        private String reportStatus;

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

        public Builder withPoints(List<InstitutionPoints> points) {
            this.points = points;
            return this;
        }

        public Builder withNotes(List<Note> notes) {
            this.notes = notes;
            return this;
        }

        public Builder withReportStatus(String reportStatus) {
            this.reportStatus = reportStatus;
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(id, publicationId, approvalStatuses, points, notes, reportStatus);
        }
    }
}
