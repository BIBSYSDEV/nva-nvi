package no.sikt.nva.nvi.common.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;
import no.sikt.nva.nvi.common.dto.VerificationStatus;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

/**
 * An NviCreator is a person registered as a 'Creator' (i.e. author or equivalent) on a publication,
 * where the publication is evaluated as a candidate for NVI reporting. The evaluation process
 * verifies that the creator is affiliated with an organization that is registered in the NVI
 * system.
 *
 * <p>An NviCreator can be either verified or unverified. A verified NviCreator has a unique ID and
 * may have a name. Creators without a confirmed identity are registered as unverified NviCreators.
 *
 * @param id Unique ID of the person as a URI, which can be used to look up additional information
 *     about them.
 * @param name The name of the person, which is used for display purposes.
 * @param verificationStatus The verification status of the person, which indicates whether their
 *     identity is confirmed.
 * @param nviAffiliations A collection of organizations that the person is directly affiliated with.
 *     These may be part of a larger organization hierarchy.
 */
public record NviCreator(
    URI id,
    String name,
    VerificationStatus verificationStatus,
    Collection<Organization> nviAffiliations) {

  public NviCreator {
    nviAffiliations = Optional.ofNullable(nviAffiliations).orElse(emptyList());
    if (isBlank(name)) {
      shouldNotBeNull(id, "Both 'id' and 'name' is null, one of these fields must be set");
    }
  }

  public static NviCreator from(NviCreatorDto creator, Collection<Organization> organizations) {
    var creatorOrganizations = getCreatorOrganizations(creator.affiliations(), organizations);
    var verificationStatus =
        new VerificationStatus(
            creator instanceof VerifiedNviCreatorDto ? "Verified" : "Unverified");
    var creatorId =
        creator instanceof VerifiedNviCreatorDto verifiedCreatorDto
            ? verifiedCreatorDto.id()
            : null;

    return new NviCreator(creatorId, creator.name(), verificationStatus, creatorOrganizations);
  }

  /**
   * Creates a NviCreator from persisted data. Because we only persist direct affiliations as URIs
   * on each creator object, we need to reconstruct the Organization tree using a collection of all
   * relevant organizations.
   *
   * @param creator A verified or unverified NVI Creator.
   * @param organizations A collection of organizations that includes all relevant organizations for
   *     this creator.
   */
  public static NviCreator from(DbCreatorType creator, Collection<Organization> organizations) {
    var creatorOrganizations = getCreatorOrganizations(creator.affiliations(), organizations);
    var verificationStatus =
        (creator instanceof DbCreator)
            ? new VerificationStatus("Verified")
            : new VerificationStatus("Unverified");
    var creatorId = (creator instanceof DbCreator) ? ((DbCreator) creator).creatorId() : null;

    return new NviCreator(
        creatorId, creator.creatorName(), verificationStatus, creatorOrganizations);
  }

  public List<URI> getAffiliationIds() {
    return nviAffiliations.stream().map(Organization::id).toList();
  }

  private static List<Organization> getCreatorOrganizations(
      Collection<URI> affiliations, Collection<Organization> topLevelOrganizations) {
    return affiliations.stream().map(id -> findOrganization(id, topLevelOrganizations)).toList();
  }

  private static Organization findOrganization(
      URI affiliationId, Collection<Organization> topLevelOrganizations) {
    // Try to find a matching organization in the persisted data
    var stack = new ArrayDeque<>(topLevelOrganizations);
    while (!stack.isEmpty()) {
      var organization = stack.pop();
      if (organization.id().equals(affiliationId)) {
        return organization;
      }
      if (nonNull(organization.hasPart()) && !organization.hasPart().isEmpty()) {
        stack.addAll(organization.hasPart());
      }
    }

    // Fall back to creating a new organization if not found, using just the ID
    return Organization.builder().withId(affiliationId).build();
  }

  public NviCreatorDto toDto() {
    var affiliationUris = nviAffiliations.stream().map(Organization::id).toList();
    if (isVerified()) {
      return new VerifiedNviCreatorDto(id, name, affiliationUris);
    }
    return new UnverifiedNviCreatorDto(name, affiliationUris);
  }

  public boolean isVerified() {
    return nonNull(id) && verificationStatus.isVerified();
  }

  public DbCreatorType toDbCreatorType() {
    var affiliationUris = nviAffiliations.stream().map(Organization::id).toList();
    if (isVerified()) {
      return new DbCreator(id, name, affiliationUris);
    }
    return new DbUnverifiedCreator(name, affiliationUris);
  }
}
