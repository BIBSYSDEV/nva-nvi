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
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;

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

  public Approval build() {
    return getBuilder().build();
  }

  public Approval.Builder getBuilder() {
    return Approval.builder()
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

  private InstitutionPoints getInstitutionPoints() {
    var creatorAffiliationPoints =
        creatorPoints.entrySet().stream()
            .map(
                tuple ->
                    new InstitutionPoints.CreatorAffiliationPoints(
                        randomUri(), tuple.getKey(), tuple.getValue()))
            .toList();

    var totalPoints =
        creatorAffiliationPoints.stream()
            .map(InstitutionPoints.CreatorAffiliationPoints::points)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.HALF_UP);

    return new InstitutionPoints(topLevelOrganization, totalPoints, creatorAffiliationPoints);
  }
}
