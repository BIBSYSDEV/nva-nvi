package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public final class Contributor {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String ORCID = "orcid";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(NAME)
    private final String name;
    @JsonProperty(ORCID)
    private final String orcId;

    public Contributor(@JsonProperty(ID) String id,
                       @JsonProperty(NAME) String name,
                       @JsonProperty(ORCID) String orcId) {
        this.id = id;
        this.name = name;
        this.orcId = orcId;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, name, orcId);
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
        var that = (Contributor) obj;
        return Objects.equals(this.id, that.id)
               && Objects.equals(this.name, that.name)
               && Objects.equals(this.orcId, that.orcId);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "Contributor["
               + "id=" + id + ", "
               + "name=" + name + ", "
               + "orcId=" + orcId + ']';
    }
}
