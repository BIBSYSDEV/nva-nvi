package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PublicationDetails(String id,
                                 String type,
                                 String title,
                                 PublicationDate publicationDate,
                                 List<ContributorType> contributors,
                                 PublicationChannel publicationChannel,
                                 Pages pages,
                                 String language) {

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnore
    public List<NviContributor> nviContributors() {
        return contributors.stream()
                   .filter(NviContributor.class::isInstance)
                   .map(NviContributor.class::cast)
                   .toList();
    }

    public static final class Builder {

        private String id;
        private String type;
        private String title;
        private PublicationDate publicationDate;
        private List<ContributorType> contributors;
        private PublicationChannel publicationChannel;
        private Pages pages;
        private String language;

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

        public Builder withPublicationChannel(PublicationChannel publicationChannel) {
            this.publicationChannel = publicationChannel;
            return this;
        }

        public Builder withPages(Pages pages) {
            this.pages = pages;
            return this;
        }

        public Builder withLanguage(String language) {
            this.language = language;
            return this;
        }

        public PublicationDetails build() {
            return new PublicationDetails(id, type, title, publicationDate, contributors, publicationChannel, pages,
                                          language);
        }
    }
}
