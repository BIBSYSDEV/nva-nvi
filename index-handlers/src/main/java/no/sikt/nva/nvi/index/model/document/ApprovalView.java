package no.sikt.nva.nvi.index.model.document;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Approval")
public record ApprovalView(
    URI institutionId,
    List<OrganizationSummary> organizationSummaries,
    Map<String, String> labels,
    ApprovalStatus approvalStatus,
    InstitutionPointsView points,
    Set<URI> involvedOrganizations,
    String assignee,
    GlobalApprovalStatus globalApprovalStatus) {

  public ApprovalView(
      URI institutionId,
      Map<String, String> labels,
      ApprovalStatus approvalStatus,
      InstitutionPointsView points,
      Set<URI> involvedOrganizations,
      String assignee,
      GlobalApprovalStatus globalApprovalStatus) {
    this(
        institutionId,
        getOrganizationSummaries(points, approvalStatus, globalApprovalStatus),
        labels,
        approvalStatus,
        points,
        involvedOrganizations,
        assignee,
        globalApprovalStatus);
  }

  private static List<OrganizationSummary> getOrganizationSummaries(
      InstitutionPointsView institutionPoints,
      ApprovalStatus approvalStatus,
      GlobalApprovalStatus globalApprovalStatus) {
    var pointsPerOrganization = getPointsPerOrganization(institutionPoints);

    return pointsPerOrganization.entrySet().stream()
        .map(
            entry ->
                new OrganizationSummary(
                    entry.getKey(), entry.getValue(), approvalStatus, globalApprovalStatus))
        .toList();
  }

  private static Map<URI, BigDecimal> getPointsPerOrganization(
      InstitutionPointsView institutionPoints) {
    var creatorPoints =
        Optional.ofNullable(institutionPoints)
            .map(InstitutionPointsView::creatorAffiliationPoints)
            .orElse(emptyList());
    return creatorPoints.stream()
        .collect(
            Collectors.groupingBy(
                InstitutionPointsView.CreatorAffiliationPointsView::affiliationId,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    InstitutionPointsView.CreatorAffiliationPointsView::points,
                    BigDecimal::add)));
  }

  public static Builder builder() {
    return new Builder();
  }

  public BigDecimal getPointsForAffiliation(
      NviContributor nviContributor, NviOrganization affiliation) {
    return points.creatorAffiliationPoints().stream()
        .filter(hasAffiliationId(affiliation))
        .filter(hasContributor(nviContributor))
        .map(CreatorAffiliationPointsView::points)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static Predicate<CreatorAffiliationPointsView> hasContributor(
      NviContributor nviContributor) {
    return creatorAffiliationPoints ->
        creatorAffiliationPoints.nviCreator().toString().equals(nviContributor.id());
  }

  private static Predicate<CreatorAffiliationPointsView> hasAffiliationId(
      NviOrganization affiliation) {
    return creatorAffiliationPoints ->
        creatorAffiliationPoints.affiliationId().equals(affiliation.id());
  }

  public static final class Builder {

    private URI institutionId;
    private Map<String, String> labels;
    private ApprovalStatus approvalStatus;
    private InstitutionPointsView points;
    private Set<URI> involvedOrganizations;
    private String assignee;
    private GlobalApprovalStatus globalApprovalStatus;

    private Builder() {}

    public Builder withInstitutionId(URI institutionId) {
      this.institutionId = institutionId;
      return this;
    }

    public Builder withLabels(Map<String, String> labels) {
      this.labels = labels;
      return this;
    }

    public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
      this.approvalStatus = approvalStatus;
      return this;
    }

    public Builder withPoints(InstitutionPointsView points) {
      this.points = points;
      return this;
    }

    public Builder withInvolvedOrganizations(Set<URI> involvedOrganizations) {
      this.involvedOrganizations = involvedOrganizations;
      return this;
    }

    public Builder withAssignee(String assignee) {
      this.assignee = assignee;
      return this;
    }

    public Builder withGlobalApprovalStatus(GlobalApprovalStatus globalApprovalStatus) {
      this.globalApprovalStatus = globalApprovalStatus;
      return this;
    }

    public ApprovalView build() {
      return new ApprovalView(
          institutionId,
          labels,
          approvalStatus,
          points,
          involvedOrganizations,
          assignee,
          globalApprovalStatus);
    }
  }
}
