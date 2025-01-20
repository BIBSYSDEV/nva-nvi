package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nva.commons.core.JacocoGenerated;

public class CristinUser {

  @JsonProperty("personlopenr")
  private String identifier;

  @JacocoGenerated
  @JsonCreator
  public CristinUser() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getIdentifier() {
    return identifier;
  }

  public static final class Builder {

    private String identifier;

    private Builder() {}

    public Builder withIdentifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public CristinUser build() {
      var cristinUser = new CristinUser();
      cristinUser.identifier = this.identifier;
      return cristinUser;
    }
  }
}
