package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public final class Affiliation {

    private static final String ID = "id";
    private static final String LABELS = "labels";
    private static final String APPROVAL_STATUS = "approvalStatus";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(LABELS)
    private final Map<String, String> labels;
    @JsonProperty(APPROVAL_STATUS)
    private final String approvalStatus;

    public Affiliation(@JsonProperty(ID) String id,
                       @JsonProperty(LABELS) Map<String, String> labels,
                       @JsonProperty(APPROVAL_STATUS) String approvalStatus) {
        this.id = id;
        this.labels = labels;
        this.approvalStatus = approvalStatus;
    }

    @JsonProperty(ID)
    public String id() {
        return id;
    }

    @JsonProperty(LABELS)
    public Map<String, String> labels() {
        return labels;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, labels, approvalStatus);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (Affiliation) obj;
        return Objects.equals(this.id, that.id)
               && Objects.equals(this.labels, that.labels)
               && Objects.equals(this.approvalStatus, that.approvalStatus);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "Affiliation["
               + "id=" + id + ", "
               + "labels=" + labels + ", "
               + "approvalStatus=" + approvalStatus + ']';
    }
}
