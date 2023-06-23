package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public final class PublicationChannel {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String LEVEL = "level";
    private static final String TYPE = "type";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(NAME)
    private final String name;
    @JsonProperty(LEVEL)
    private final String level;
    @JsonProperty(TYPE)
    private final String type;

    public PublicationChannel(@JsonProperty(ID) String id,
                              @JsonProperty(NAME) String name,
                              @JsonProperty(LEVEL) String level,
                              @JsonProperty(TYPE) String type) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.type = type;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, name, level, type);
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
        var that = (PublicationChannel) obj;
        return Objects.equals(this.id, that.id)
               && Objects.equals(this.name, that.name)
               && Objects.equals(this.level, that.level)
               && Objects.equals(this.type, that.type);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "PublicationChannel["
               + "id=" + id + ", "
               + "name=" + name + ", "
               + "level=" + level + ", "
               + "type=" + type + ']';
    }
}
