package no.sikt.nva.nvi.common.dto;

import static java.util.Objects.requireNonNull;
import static no.sikt.nva.nvi.common.utils.Validator.shouldBeTrue;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;

@JsonSerialize
public record PublicationChannelDto(
    URI id,
    ChannelType channelType,
    ScientificValue scientificValue,
    String identifier,
    String name,
    String year,
    String onlineIssn,
    String printIssn) {

  public PublicationChannelDto {
    requireNonNull(id, "Required field 'id' is null");
  }

  public void validate() {
    shouldNotBeNull(channelType, "Required field 'channelType' is null");
    shouldNotBeNull(scientificValue, "Required field 'scientificValue' is null");

    shouldBeTrue(channelType().isValid(), "Required field 'channelType' is invalid");
    shouldBeTrue(scientificValue().isValid(), "Required field 'scientificValue' is invalid");
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private ChannelType channelType;
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

    public Builder withChannelType(ChannelType channelType) {
      this.channelType = channelType;
      return this;
    }

    public Builder withScientificValue(ScientificValue scientificValue) {
      this.scientificValue = scientificValue;
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
          id, channelType, scientificValue, identifier, name, year, onlineIssn, printIssn);
    }
  }
}
