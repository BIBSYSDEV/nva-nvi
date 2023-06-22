package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Publication(@JsonProperty(ID) String id,
                          @JsonProperty(TYPE) String type,
                          @JsonProperty(TITLE) String title,
                          @JsonProperty(PUBLICATION_DATE) String publicationDate,
                          @JsonProperty(PUBLICATION_CHANNEL) PublicationChannel publicationChannel,
                          @JsonProperty(CONTRIBUTORS) List<Contributor> contributors) {

    public static final String ID = "id";
    public static final String TYPE = "type";
    public static final String TITLE = "title";
    public static final String PUBLICATION_DATE = "publicationDate";
    public static final String PUBLICATION_CHANNEL = "publicationChannel";
    public static final String CONTRIBUTORS = "contributors";
}
