package no.sikt.nva.nvi.common.dto;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import no.sikt.nva.nvi.common.model.ScientificValue;
import nva.commons.core.JacocoGenerated;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("PublicationChannel")
public record PublicationChannelDto(
    URI id,
    String channelType,
    String identifier,
    String name,
    String year,
    ScientificValue scientificValue,
    String onlineIssn,
    String printIssn) {

  public PublicationChannelDto {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(channelType, "Required field 'channelType' is null");
    requireNonNull(scientificValue, "Required field 'scientificValue' is null");
  }

  public void validate() {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(channelType, "Required field 'channelType' is null");
    if (!scientificValue.isValid()) {
      throw new IllegalArgumentException("Invalid scientific value");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String channelType;
    private String identifier;
    private String name;
    private String year;
    private ScientificValue scientificValue;
    private String onlineIssn;
    private String printIssn;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withChannelType(String channelType) {
      this.channelType = channelType;
      return this;
    }

    public Builder withIdentifier(String identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withYear(String year) {
      this.year = year;
      return this;
    }

    public Builder withScientificValue(ScientificValue scientificValue) {
      this.scientificValue = scientificValue;
      return this;
    }

    @JacocoGenerated
    public Builder withOnlineIssn(String onlineIssn) {
      this.onlineIssn = onlineIssn;
      return this;
    }

    public Builder withPrintIssn(String printIssn) {
      this.printIssn = printIssn;
      return this;
    }

    public PublicationChannelDto build() {
      return new PublicationChannelDto(
          id, channelType, identifier, name, year, scientificValue, onlineIssn, printIssn);
    }
  }
}
