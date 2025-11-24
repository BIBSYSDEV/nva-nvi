package no.sikt.nva.nvi.index.model;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.test.TestUtils.SCALE;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;

/**
 * Test utility for building Approval instances with related values kept in sync automatically.
 * Creator points are summed to calculate total institution points, and involved organizations are
 * populated based on creator affiliations, ensuring test data consistency.
 */
public class ApprovalFactory {
  private final Map<URI, BigDecimal> creatorPoints;
  private final URI topLevelOrganization;
  private ApprovalStatus approvalStatus;
  private GlobalApprovalStatus globalApprovalStatus;

  public ApprovalFactory(URI topLevelOrganization) {
    this.topLevelOrganization = topLevelOrganization;
    this.creatorPoints = new HashMap<>();
    this.approvalStatus = ApprovalStatus.PENDING;
    this.globalApprovalStatus = GlobalApprovalStatus.PENDING;
  }

  public ApprovalFactory(
      URI topLevelOrganization,
      Map<URI, BigDecimal> creatorPoints,
      ApprovalStatus approvalStatus,
      GlobalApprovalStatus globalApprovalStatus) {
    this.topLevelOrganization = topLevelOrganization;
    this.creatorPoints = new HashMap<>(creatorPoints);
    this.approvalStatus = approvalStatus;
    this.globalApprovalStatus = globalApprovalStatus;
  }

  public ApprovalView build() {
    return getBuilder().build();
  }

  public ApprovalView.Builder getBuilder() {
    return ApprovalView.builder()
        .withInstitutionId(topLevelOrganization)
        .withLabels(emptyMap())
        .withAssignee(randomString())
        .withApprovalStatus(approvalStatus)
        .withGlobalApprovalStatus(globalApprovalStatus)
        .withInvolvedOrganizations(getInvolvedOrganizations())
        .withPoints(getInstitutionPoints());
  }

  public ApprovalFactory copy() {
    return new ApprovalFactory(
        topLevelOrganization, creatorPoints, approvalStatus, globalApprovalStatus);
  }

  public ApprovalFactory withApprovalStatus(ApprovalStatus approvalStatus) {
    this.approvalStatus = approvalStatus;
    return this;
  }

  public ApprovalFactory withGlobalApprovalStatus(GlobalApprovalStatus globalApprovalStatus) {
    this.globalApprovalStatus = globalApprovalStatus;
    return this;
  }

  public ApprovalFactory withCreatorAffiliation(URI affiliation) {
    this.creatorPoints.put(affiliation, randomBigDecimal(SCALE));
    return this;
  }

  public ApprovalFactory withCreatorAffiliation(URI affiliation, BigDecimal points) {
    this.creatorPoints.put(affiliation, points);
    return this;
  }

  private Set<URI> getInvolvedOrganizations() {
    var involvedOrganizations = new HashSet<>(creatorPoints.keySet());
    involvedOrganizations.add(topLevelOrganization);
    return Set.copyOf(involvedOrganizations);
  }

  private InstitutionPointsView getInstitutionPoints() {
    var creatorAffiliationPoints =
        creatorPoints.entrySet().stream()
            .map(
                tuple ->
                    new InstitutionPointsView.CreatorAffiliationPointsView(
                        randomUri(), tuple.getKey(), tuple.getValue()))
            .toList();

    var totalPoints =
        creatorAffiliationPoints.stream()
            .map(InstitutionPointsView.CreatorAffiliationPointsView::points)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.HALF_UP);

    return new InstitutionPointsView(topLevelOrganization, totalPoints, creatorAffiliationPoints);
  }
}
