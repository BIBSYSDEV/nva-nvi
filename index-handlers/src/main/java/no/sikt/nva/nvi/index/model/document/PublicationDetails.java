package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import nva.commons.core.paths.UriWrapper;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PublicationDetails(
    String id,
    String identifier,
    String type,
    String title,
    @JsonProperty("abstract") String abstractText,
    PublicationDateDto publicationDate,
    List<NviContributor> nviContributors,
    List<ContributorType> contributors,
    int contributorsCount,
    PublicationChannel publicationChannel,
    Pages pages,
    String language) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String id;
    private String identifier;
    private String type;
    private String title;
    private String abstractText;
    private PublicationDateDto publicationDate;
    private List<NviContributor> nviContributors;
    private List<ContributorType> contributors;
    private int contributorsCount;
    private PublicationChannel publicationChannel;
    private Pages pages;
    private String language;

    private Builder() {}

    public Builder withId(String id) {
      this.id = id;
      this.identifier = UriWrapper.fromUri(id).getLastPathElement();
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

    public Builder withAbstract(String abstractText) {
      this.abstractText = abstractText;
      return this;
    }

    public Builder withPublicationDate(PublicationDateDto publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withContributors(List<ContributorType> contributors) {
      this.contributors = contributors;
      this.contributorsCount = contributors.size();
      this.nviContributors =
          contributors.stream()
              .filter(NviContributor.class::isInstance)
              .map(NviContributor.class::cast)
              .toList();
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
      return new PublicationDetails(
          id,
          identifier,
          type,
          title,
          abstractText,
          publicationDate,
          nviContributors,
          contributors,
          contributorsCount,
          publicationChannel,
          pages,
          language);
    }
  }
}
