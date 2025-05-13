package no.sikt.nva.nvi.common.client.model;

import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import no.sikt.nva.nvi.common.db.model.DbOrganization;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.SingletonCollector;

public record Organization(
    @JsonProperty("id") URI id,
    @JsonProperty("country") String countryCode,
    @JsonProperty("partOf") List<Organization> partOf,
    @JsonProperty("hasPart") List<Organization> hasPart,
    @JsonProperty("labels") Map<String, String> labels,
    @JsonProperty("type") String type,
    @JsonProperty("@context") Object context)
    implements JsonSerializable {

  public static Organization from(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, Organization.class);
  }

  public static Organization from(DbOrganization dbOrganization) {
    return builder()
        .withId(dbOrganization.id())
        .withCountryCode(dbOrganization.countryCode())
        .withPartOf(mapIfNotEmpty(dbOrganization.parentOrganizations(), Organization::from))
        .withHasPart(mapIfNotEmpty(dbOrganization.subOrganizations(), Organization::from))
        .withLabels(Optional.ofNullable(dbOrganization.labels()).orElse(null))
        .build();
  }

  public static DbOrganization toDbOrganization(Organization organization) {
    return DbOrganization.builder()
        .id(organization.id())
        .countryCode(organization.countryCode())
        .parentOrganizations(mapIfNotEmpty(organization.partOf(), Organization::toDbOrganization))
        .subOrganizations(mapIfNotEmpty(organization.hasPart(), Organization::toDbOrganization))
        .labels(Optional.ofNullable(organization.labels()).orElse(null))
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  @JsonIgnore
  public Organization getTopLevelOrg() {
    if (nonNull(partOf()) && !partOf().isEmpty()) {

      var organization = partOf().stream().collect(SingletonCollector.collect());

      while (hasPartOf(organization)) {
        organization = organization.partOf().stream().collect(SingletonCollector.collect());
      }

      return organization;
    }

    return this;
  }

  private static <T, R> List<R> mapIfNotEmpty(Collection<T> src, Function<T, R> mapper) {
    return Optional.ofNullable(src)
        .filter(not(Collection::isEmpty))
        .map(list -> list.stream().map(mapper).toList())
        .orElse(null);
  }

  private static boolean hasPartOf(Organization org) {
    return nonNull(org.partOf()) && !org.partOf().isEmpty();
  }

  public static final class Builder {

    private URI id;
    private String countryCode;
    private List<Organization> parentOrganizations;
    private List<Organization> subOrganizations;
    private Map<String, String> labels;
    private String type;
    private Object context;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withCountryCode(String countryCode) {
      this.countryCode = countryCode;
      return this;
    }

    public Builder withPartOf(List<Organization> partOf) {
      this.parentOrganizations = partOf;
      return this;
    }

    public Builder withHasPart(List<Organization> hasPart) {
      this.subOrganizations = hasPart;
      return this;
    }

    public Builder withLabels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public Builder withType(String type) {
      this.type = type;
      return this;
    }

    public Builder withContext(Object context) {
      this.context = context;
      return this;
    }

    public Organization build() {
      return new Organization(
          id, countryCode, parentOrganizations, subOrganizations, labels, type, context);
    }
  }
}
