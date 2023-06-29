package no.sikt.nva.nvi.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class Candidate {

    @JsonProperty("publicationId")
    private final URI publicationId;
    @JsonProperty("period")
    private final NviPeriod period;
    @JsonProperty("foundation")
    private final Foundation foundation;
    @JsonProperty("institutionStatuses")
    private final List<InstitutionStatus> institutionStatuses;
    @JsonProperty("notes")
    private final List<Note> notes;

    public Candidate(Builder builder) {
        this.publicationId = builder.publicationId;
        this.period = builder.period;
        this.foundation = builder.foundation;
        this.institutionStatuses = builder.institutionStatuses;
        this.notes = builder.notes;
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getPublicationId(), getPeriod(), getFoundation(), getInstitutionStatuses(), getNotes());
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Candidate candidate = (Candidate) o;
        return Objects.equals(getPublicationId(), candidate.getPublicationId())
               && Objects.equals(getPeriod(), candidate.getPeriod())
               && Objects.equals(getFoundation(), candidate.getFoundation())
               && Objects.equals(getInstitutionStatuses(), candidate.getInstitutionStatuses())
               && Objects.equals(getNotes(), candidate.getNotes());
    }

    public URI getPublicationId() {
        return publicationId;
    }

    public NviPeriod getPeriod() {
        return period;
    }

    public Foundation getFoundation() {
        return foundation;
    }

    public List<InstitutionStatus> getInstitutionStatuses() {
        return institutionStatuses;
    }

    public List<Note> getNotes() {
        return notes;
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
            return new Candidate(this);
        }
    }
}
