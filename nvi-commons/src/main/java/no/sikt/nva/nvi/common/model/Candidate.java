package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Candidate(URI publicationId,
                        NviPeriod period,
                        Foundation foundation,
                        List<InstitutionStatus> institutionStatuses,
                        List<Note> notes) {

    public Builder copy() {
        return new Builder()
                   .withPublicationId(this.publicationId)
                   .withPeriod(this.period)
                   .withFoundation(this.foundation)
                   .withInstitutionStatuses(this.institutionStatuses)
                   .withNotes(this.notes);
    }

    public static class Builder {

        private URI publicationId;
        private NviPeriod period;
        private Foundation foundation;
        private List<InstitutionStatus> institutionStatuses;
        private List<Note> notes;

        public Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withPeriod(NviPeriod period) {
            this.period = period;
            return this;
        }

        public Builder withFoundation(Foundation foundation) {
            this.foundation = foundation;
            return this;
        }

        public Builder withInstitutionStatuses(List<InstitutionStatus> institutionStatuses) {
            this.institutionStatuses = institutionStatuses;
            return this;
        }

        public Builder withNotes(List<Note> notes) {
            this.notes = notes;
            return this;
        }

        public Candidate build() {
            return new Candidate(publicationId, period, foundation, institutionStatuses, notes);
        }
    }
}
