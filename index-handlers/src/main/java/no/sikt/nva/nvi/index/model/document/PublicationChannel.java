package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import no.sikt.nva.nvi.common.model.ScientificValue;

@JsonSerialize
public record PublicationChannel(
    URI id, String type, ScientificValue scientificValue, String name, String printIssn) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private URI id;
    private String type;
    private ScientificValue scientificValue;
    private String name;
    private String printIssn;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    public Builder withScientificValue(ScientificValue scientificValue) {
      this.scientificValue = scientificValue;
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
