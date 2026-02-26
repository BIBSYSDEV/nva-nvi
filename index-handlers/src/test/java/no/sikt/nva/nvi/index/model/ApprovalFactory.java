package no.sikt.nva.nvi.index.model;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.test.TestUtils.SCALE;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.model.Sector;
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
  private final List<InstitutionPointsView.CreatorAffiliationPointsView> creatorAffiliationPoints;
  private final URI topLevelOrganization;
  private ApprovalStatus approvalStatus;
  private GlobalApprovalStatus globalApprovalStatus;
  private Sector sector = Sector.UNKNOWN;

  public ApprovalFactory(URI topLevelOrganization) {
    this.topLevelOrganization = topLevelOrganization;
    this.creatorAffiliationPoints = new ArrayList<>();
    this.approvalStatus = ApprovalStatus.PENDING;
    this.globalApprovalStatus = GlobalApprovalStatus.PENDING;
  }

  public ApprovalFactory(
      URI topLevelOrganization,
      List<InstitutionPointsView.CreatorAffiliationPointsView> affiliationPoints,
      ApprovalStatus approvalStatus,
      GlobalApprovalStatus globalApprovalStatus,
      Sector sector) {
    this.topLevelOrganization = topLevelOrganization;
    this.creatorAffiliationPoints = new ArrayList<>(affiliationPoints);
    this.approvalStatus = approvalStatus;
    this.globalApprovalStatus = globalApprovalStatus;
    this.sector = sector;
  }

  public ApprovalView toApproval(GlobalApprovalStatus globalStatus, ApprovalStatus localStatus) {
    return getBuilder().build();
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
        .withSector(sector.toString())
        .withPoints(getInstitutionPoints());
  }

  public ApprovalFactory copy() {
    return new ApprovalFactory(
        topLevelOrganization,
        creatorAffiliationPoints,
        approvalStatus,
        globalApprovalStatus,
        sector);
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
    return this.withCreatorAffiliation(affiliation, randomBigDecimal(SCALE));
  }

  public ApprovalFactory withCreatorAffiliation(URI affiliation, BigDecimal points) {
    this.creatorAffiliationPoints.add(
        new InstitutionPointsView.CreatorAffiliationPointsView(randomUri(), affiliation, points));
    return this;
  }

  public ApprovalFactory withSector(Sector sector) {
    this.sector = sector;
    return this;
  }

  public ApprovalFactory withCreatorAffiliations(Collection<URI> affiliations) {
    var pointsPerCreator = randomBigDecimal(SCALE);
    for (var affiliation : affiliations) {
      this.creatorAffiliationPoints.add(
          new InstitutionPointsView.CreatorAffiliationPointsView(
              randomUri(), affiliation, pointsPerCreator));
    }
    return this;
  }

  private Set<URI> getInvolvedOrganizations() {
    var creatorAffiliations =
        creatorAffiliationPoints.stream()
            .map(InstitutionPointsView.CreatorAffiliationPointsView::affiliationId)
            .collect(Collectors.toSet());
    var involvedOrganizations = new HashSet<>(creatorAffiliations);
    involvedOrganizations.add(topLevelOrganization);
    return Set.copyOf(involvedOrganizations);
  }

  private InstitutionPointsView getInstitutionPoints() {
    var totalPoints =
        creatorAffiliationPoints.stream()
            .map(InstitutionPointsView.CreatorAffiliationPointsView::points)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.HALF_UP);

    return new InstitutionPointsView(topLevelOrganization, totalPoints, creatorAffiliationPoints);
  }
}
