package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PublicationDetails(String id, String type, String title, PublicationDate publicationDate,
                                 List<Contributor> contributors) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String type;
        private String title;
        private PublicationDate publicationDate;
        private List<Contributor> contributors;

        private Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withPublicationDate(PublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder withContributors(List<Contributor> contributors) {
            this.contributors = contributors;
            return this;
        }

        public PublicationDetails build() {
            return new PublicationDetails(id, type, title, publicationDate, contributors);
        }
    }
}
