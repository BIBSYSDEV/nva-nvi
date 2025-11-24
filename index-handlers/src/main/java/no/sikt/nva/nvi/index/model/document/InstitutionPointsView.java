package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("InstitutionPoints")
public record InstitutionPointsView(
    URI institutionId,
    BigDecimal institutionPoints,
    List<OrganizationPointsView> organizationPoints,
    List<CreatorAffiliationPointsView> creatorAffiliationPoints) {

  public InstitutionPointsView(
      URI institutionId,
      BigDecimal institutionPoints,
      Collection<CreatorAffiliationPointsView> creatorAffiliationPoints) {
    this(
        institutionId,
        institutionPoints,
        buildOrganizationPoints(creatorAffiliationPoints),
        List.copyOf(creatorAffiliationPoints));
  }

  public static InstitutionPointsView from(InstitutionPoints institutionPoints) {
    var creatorPoints =
        institutionPoints.creatorAffiliationPoints().stream()
            .map(CreatorAffiliationPointsView::from)
            .toList();
    return new InstitutionPointsView(
        institutionPoints.institutionId(),
        institutionPoints.institutionPoints(),
        buildOrganizationPoints(creatorPoints),
        creatorPoints);
  }

  private static List<OrganizationPointsView> buildOrganizationPoints(
      Collection<CreatorAffiliationPointsView> creatorPoints) {
    var pointsPerOrganization =
        creatorPoints.stream()
            .collect(
                Collectors.groupingBy(
                    CreatorAffiliationPointsView::affiliationId,
                    Collectors.reducing(
                        BigDecimal.ZERO, CreatorAffiliationPointsView::points, BigDecimal::add)));

    return pointsPerOrganization.entrySet().stream()
        .map(entry -> new OrganizationPointsView(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  public static Builder builder() {
    return new Builder();
  }

  @JsonSerialize
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonTypeName("CreatorAffiliationPoints")
  public record CreatorAffiliationPointsView(URI nviCreator, URI affiliationId, BigDecimal points) {

    public static CreatorAffiliationPointsView from(
        InstitutionPoints.CreatorAffiliationPoints creatorAffiliationPoints) {
      return new CreatorAffiliationPointsView(
          creatorAffiliationPoints.nviCreator(),
          creatorAffiliationPoints.affiliationId(),
          creatorAffiliationPoints.points());
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {

      private URI nviCreator;
      private URI affiliationId;

      private BigDecimal points;

      private Builder() {}

      public Builder withNviCreator(URI nviCreator) {
        this.nviCreator = nviCreator;
        return this;
      }

      public Builder withAffiliationId(URI affiliationId) {
        this.affiliationId = affiliationId;
        return this;
      }

      public Builder withPoints(BigDecimal points) {
        this.points = points;
        return this;
      }

      public CreatorAffiliationPointsView build() {
        return new CreatorAffiliationPointsView(nviCreator, affiliationId, points);
      }
    }
  }

  public static final class Builder {

    private URI institutionId;
    private BigDecimal institutionPoints;

    private List<CreatorAffiliationPointsView> creatorAffiliationPoints;

    private Builder() {}

    public Builder withInstitutionId(URI institutionId) {
      this.institutionId = institutionId;
      return this;
    }

    public Builder withInstitutionPoints(BigDecimal institutionPoints) {
      this.institutionPoints = institutionPoints;
      return this;
    }

    public Builder withCreatorAffiliationPoints(
        List<CreatorAffiliationPointsView> creatorAffiliationPoints) {
      this.creatorAffiliationPoints = creatorAffiliationPoints;
      return this;
    }

    public InstitutionPointsView build() {
      return new InstitutionPointsView(institutionId, institutionPoints, creatorAffiliationPoints);
    }
  }
}
