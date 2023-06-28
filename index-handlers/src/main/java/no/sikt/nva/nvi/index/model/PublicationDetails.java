package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public final class PublicationDetails {

    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String TITLE = "title";
    private static final String PUBLICATION_DATE = "publicationDate";
    private static final String CONTRIBUTORS = "contributors";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(TYPE)
    private final String type;
    @JsonProperty(TITLE)
    private final String title;
    @JsonProperty(PUBLICATION_DATE)
    private final String publicationDate;
    @JsonProperty(CONTRIBUTORS)
    private final List<Contributor> contributors;

    public PublicationDetails(@JsonProperty(ID) String id,
                              @JsonProperty(TYPE) String type,
                              @JsonProperty(TITLE) String title,
                              @JsonProperty(PUBLICATION_DATE) String publicationDate,
                              @JsonProperty(CONTRIBUTORS) List<Contributor> contributors) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.publicationDate = publicationDate;
        this.contributors = contributors;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public List<Contributor> getContributors() {
        return contributors;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, type, title, publicationDate, contributors);
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
        var that = (PublicationDetails) obj;
        return Objects.equals(this.id, that.id)
               && Objects.equals(this.type, that.type)
               && Objects.equals(this.title, that.title)
               && Objects.equals(this.publicationDate, that.publicationDate)
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
               + "contributors=" + contributors + ']';
    }
}
