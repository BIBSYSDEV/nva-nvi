package no.sikt.nva.nvi.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;

public class Candidate {

    @JsonProperty("publicationId")
    private final URI publicationId;
    @JsonProperty("period")
    private final NviPeriod period;
    private final Foundation foundation;
    private final List<InstitutionStatus> institutionStatuses;
    private final List<Note> notes;

    public Candidate(URI publicationId, NviPeriod period,
                     Foundation foundation,
                     List<InstitutionStatus> institutionStatuses,
                     List<Note> notes) {
        this.publicationId = publicationId;
        this.period = period;
        this.foundation = foundation;
        this.institutionStatuses = institutionStatuses;
        this.notes = notes;
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


}
