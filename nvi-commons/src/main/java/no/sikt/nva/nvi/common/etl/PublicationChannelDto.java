package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("PublicationChannel")
public record PublicationChannelDto(
    URI id,
    String channelType,
    String identifier,
    String name,
    String year,
    String scientificValue,
    String onlineIssn,
    String printIssn) {

  public PublicationChannelDto {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(channelType, "Required field 'channelType' is null");
    requireNonNull(scientificValue, "Required field 'scientificLevel' is null");
  }

  public void validate() {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(channelType, "Required field 'channelType' is null");
    requireNonNull(scientificValue, "Required field 'scientificLevel' is null");
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
    private String scientificValue;
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

    public Builder withScientificValue(String scientificValue) {
      this.scientificValue = scientificValue;
      return this;
    }

    public Builder withPrintIssn(String printIssn) {
      this.printIssn = printIssn;
      return this;
    }

    public PublicationChannelDto build() {
      return new PublicationChannelDto(
          id, channelType, identifier, name, year, scientificValue, null, printIssn);
    }
  }
}
