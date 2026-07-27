package no.sikt.nva.nvi.common.model;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_UNVERIFIED;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_VERIFIED;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.test.TestUtils.randomContributorId;
import static no.sikt.nva.nvi.test.TestUtils.randomName;
import static no.sikt.nva.nvi.test.TestUtils.randomOrcid;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;

public final class NviCreatorFixtures {

  private NviCreatorFixtures() {}

  public static List<DbCreatorType> mapToDbCreators(Collection<NviCreator> nviCreators) {
    return nviCreators.stream().map(NviCreator::toDbCreatorType).toList();
  }

  public static NviCreator.Builder verifiedNviCreator() {
    var institution = randomTopLevelOrganization();
    var affiliationId = institution.hasPart().getFirst().id();
    return NviCreator.builder()
        .withId(randomContributorId())
        .withName(randomName())
        .withOrcid(randomOrcid())
        .withVerificationStatus(STATUS_VERIFIED)
        .withNviAffiliations(List.of(affiliationId))
        .withTopLevelNviOrganizations(List.of(institution));
  }

  public static NviCreator randomVerifiedNviCreator() {
    return verifiedNviCreator().build();
  }

  public static NviCreator randomUnverifiedNviCreator() {
    return verifiedNviCreator().withId(null).withVerificationStatus(STATUS_UNVERIFIED).build();
  }

  public static NviCreator verifiedNviCreatorFrom(
      Organization topLevelOrganization, URI... affiliations) {
    return new NviCreator(
        randomContributorId(),
        randomName(),
        randomOrcid(),
        STATUS_VERIFIED,
        List.of(affiliations),
        List.of(topLevelOrganization));
  }

  public static NviCreator verifiedNviCreatorFrom(Organization topLevelOrganization) {
    return verifiedNviCreatorFrom(topLevelOrganization, defaultAffiliationId(topLevelOrganization));
  }

  public static NviCreator unverifiedNviCreatorFrom(
      Organization topLevelOrganization, URI... affiliations) {
    return new NviCreator(
        null,
        randomName(),
        null,
        STATUS_UNVERIFIED,
        List.of(affiliations),
        List.of(topLevelOrganization));
  }

  public static NviCreator unverifiedNviCreatorFrom(Organization topLevelOrganization) {
    return unverifiedNviCreatorFrom(
        topLevelOrganization, defaultAffiliationId(topLevelOrganization));
  }

  private static URI defaultAffiliationId(Organization topLevelOrganization) {
    var subUnits = topLevelOrganization.hasPart();
    return isNull(subUnits) || subUnits.isEmpty()
        ? topLevelOrganization.id()
        : subUnits.getFirst().id();
  }

  public static NviCreator verifiedCopyOf(NviCreator creator) {
    return creator
        .copy()
        .withId(randomContributorId())
        .withVerificationStatus(STATUS_VERIFIED)
        .build();
  }

  public static NviCreator unverifiedCopyOf(NviCreator creator) {
    return creator.copy().withId(null).withVerificationStatus(STATUS_UNVERIFIED).build();
  }

  public static NviCreator copyAffiliatedWith(
      NviCreator creator, Organization newTopLevelOrganization) {
    var affiliationId = newTopLevelOrganization.hasPart().getFirst().id();
    return creator
        .copy()
        .withNviAffiliations(List.of(affiliationId))
        .withTopLevelNviOrganizations(List.of(newTopLevelOrganization))
        .build();
  }
}
