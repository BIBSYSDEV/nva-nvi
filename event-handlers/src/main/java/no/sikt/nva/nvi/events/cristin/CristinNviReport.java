package no.sikt.nva.nvi.events.cristin;

import static java.util.Objects.nonNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.model.PublicationDate;
import no.unit.nva.commons.json.JsonSerializable;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CristinNviReport(
    String publicationIdentifier,
    String cristinIdentifier,
    List<ScientificResource> scientificResources,
    List<CristinLocale> cristinLocales,
    String yearReported,
    PublicationDate publicationDate,
    String instanceType,
    JsonNode reference)
    implements JsonSerializable {

  public List<CristinLocale> cristinLocales() {
    return nonNull(cristinLocales) ? cristinLocales : List.of();
  }

  @JsonIgnore
  public DbLevel getLevel() {
    return scientificResources().stream()
        .map(ScientificResource::getQualityCode)
        .map(DbLevel::fromDeprecatedValue)
        .findFirst()
        .orElseThrow();
  }

  public List<ScientificResource> scientificResources() {
    return nonNull(scientificResources) ? scientificResources : List.of();
  }

  @JsonIgnore
  public Optional<String> getYearReportedFromHistoricalData() {
    return scientificResources().stream()
        .map(ScientificResource::getReportedYear)
        .filter(Objects::nonNull)
        .findFirst();
  }

  public List<ScientificPerson> getCreators() {
    return scientificResources().stream()
        .flatMap(resource -> resource.getCreators().stream())
        .toList();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String publicationIdentifier;
    private String cristinIdentifier;
    private List<CristinLocale> cristinLocales;
    private String yearReported;
    private PublicationDate publicationDate;
    private List<ScientificResource> scientificResources;
    private String instanceType;
    private JsonNode reference;

    private Builder() {}

    public Builder withPublicationIdentifier(String publicationIdentifier) {
      this.publicationIdentifier = publicationIdentifier;
      return this;
    }

    public Builder withCristinIdentifier(String cristinIdentifier) {
      this.cristinIdentifier = cristinIdentifier;
      return this;
    }

    public Builder withCristinLocales(List<CristinLocale> nviReport) {
      this.cristinLocales = nviReport;
      return this;
    }

    public Builder withScientificResources(List<ScientificResource> scientificResources) {
      this.scientificResources = scientificResources;
      return this;
    }

    public Builder withYearReported(String yearReported) {
      this.yearReported = yearReported;
      return this;
    }

    public Builder withPublicationDate(PublicationDate publicationDate) {
      this.publicationDate = publicationDate;
      return this;
    }

    public Builder withInstanceType(String instanceType) {
      this.instanceType = instanceType;
      return this;
    }

    public Builder withReference(JsonNode reference) {
      this.reference = reference;
      return this;
    }

    public CristinNviReport build() {
      return new CristinNviReport(
          publicationIdentifier,
          cristinIdentifier,
          scientificResources,
          cristinLocales,
          yearReported,
          publicationDate,
          instanceType,
          reference);
    }
  }
}
