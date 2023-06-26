package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Publication(@JsonProperty(ID) String id,
                          @JsonProperty(TYPE) String type,
                          @JsonProperty(TITLE) String title,
                          @JsonProperty(PUBLICATION_DATE) String publicationDate,
                          @JsonProperty(CONTRIBUTORS) List<Contributor> contributors) {

    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String TITLE = "title";
    private static final String PUBLICATION_DATE = "publicationDate";
    private static final String CONTRIBUTORS = "contributors";
}
