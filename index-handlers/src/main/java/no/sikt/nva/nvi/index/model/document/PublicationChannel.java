package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;

@JsonSerialize
public record PublicationChannel(
    URI id, String type, String scientificValue, String name, String printIssn) {

  public static PublicationChannel from(no.sikt.nva.nvi.common.model.PublicationChannel persisted) {
    return builder()
        .withScientificValue(persisted.scientificValue())
        .withId(persisted.id())
        .withType(persisted.channelType())
        .build();
  }

  public static PublicationChannel from(
      no.sikt.nva.nvi.common.model.PublicationChannel persisted, PublicationChannelDto current) {
    return builder()
        .withScientificValue(persisted.scientificValue())
        .withId(persisted.id())
        .withType(persisted.channelType())
        .withName(current.name())
        .withPrintIssn(current.printIssn())
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String type;
    private String scientificValue;
    private String name;
    private String printIssn;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withType(ChannelType type) {
      this.type = Optional.ofNullable(type).map(ChannelType::getValue).orElse(null);
      return this;
    }

    public Builder withScientificValue(ScientificValue scientificValue) {
      this.scientificValue =
          Optional.ofNullable(scientificValue).map(ScientificValue::getValue).orElse(null);
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withPrintIssn(String printIssn) {
      this.printIssn = printIssn;
      return this;
    }

    public PublicationChannel build() {
      return new PublicationChannel(id, type, scientificValue, name, printIssn);
    }
  }
}
