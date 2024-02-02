package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PublicationDetails(@JsonProperty("id") String id,
                                 @JsonProperty("type") String type,
                                 @JsonProperty("title") String title,
                                 @JsonProperty("publicationDate") PublicationDate publicationDate,
                                 @JsonProperty("contributors") List<ContributorType> contributors) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String type;
        private String title;
        private PublicationDate publicationDate;
        private List<ContributorType> contributors;

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

        public Builder withContributors(List<ContributorType> contributors) {
            this.contributors = contributors;
            return this;
        }

        public PublicationDetails build() {
            return new PublicationDetails(id, type, title, publicationDate, contributors);
        }
    }
}
