package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public final class Publication {

    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String TITLE = "title";
    private static final String PUBLICATION_DATE = "publicationDate";
    private static final String PUBLICATION_CHANNEL = "publicationChannel";
    private static final String CONTRIBUTORS = "contributors";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(TYPE)
    private final String type;
    @JsonProperty(TITLE)
    private final String title;
    @JsonProperty(PUBLICATION_DATE)
    private final String publicationDate;
    @JsonProperty(PUBLICATION_CHANNEL)
    private final PublicationChannel publicationChannel;
    @JsonProperty(CONTRIBUTORS)
    private final List<Contributor> contributors;

    public Publication(@JsonProperty(ID) String id,
                       @JsonProperty(TYPE) String type,
                       @JsonProperty(TITLE) String title,
                       @JsonProperty(PUBLICATION_DATE) String publicationDate,
                       @JsonProperty(PUBLICATION_CHANNEL) PublicationChannel publicationChannel,
                       @JsonProperty(CONTRIBUTORS) List<Contributor> contributors) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.publicationDate = publicationDate;
        this.publicationChannel = publicationChannel;
        this.contributors = contributors;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, type, title, publicationDate, publicationChannel, contributors);
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
        var that = (Publication) obj;
        return Objects.equals(this.id, that.id)
               && Objects.equals(this.type, that.type)
               && Objects.equals(this.title, that.title)
               && Objects.equals(this.publicationDate, that.publicationDate)
               && Objects.equals(this.publicationChannel, that.publicationChannel)
               && Objects.equals(this.contributors, that.contributors);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "Publication["
               + "id=" + id + ", "
               + "type=" + type + ", "
               + "title=" + title + ", "
               + "publicationDate=" + publicationDate + ", "
               + "publicationChannel=" + publicationChannel + ", "
               + "contributors=" + contributors + ']';
    }
}
