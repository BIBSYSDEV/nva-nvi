package no.sikt.nva.nvi.common.service.dto;

import static nva.commons.core.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record VerifiedNviCreatorDto(URI id, String name, List<URI> affiliations)
    implements NviCreatorDto {

  public static Builder builder() {
    return new Builder();
  }

  // FIXME: Remove this?
  @Override
  public DbCreator toDao() {
    return new DbCreator(id, name, affiliations);
  }

  public static final class Builder {

    private URI id;
    private String name;
    private List<URI> affiliations = Collections.emptyList();

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withAffiliations(List<URI> affiliations) {
      this.affiliations = affiliations;
      return this;
    }

    public VerifiedNviCreatorDto build() {
      if (isBlank(id.toString())) {
        throw new IllegalStateException("ID cannot be null or blank");
      }
      return new VerifiedNviCreatorDto(id, name, affiliations);
    }
  }
}
