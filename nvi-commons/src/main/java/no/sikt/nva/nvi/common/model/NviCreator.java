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
 * @param id Unique ID as a URI, which can be dereferenced for more information.
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

  /**
   * Creates a NviCreator domain model from its DTO representation and a collection of
   * organizations. The complete organization hierarchy for a Candidate is stored as a separate
   * field and not duplicated to each creator, and must therefore be reconstructed separately here.
   *
   * @param creator A simplified DTO representation of a verified or unverified NVI Creator
   * @param organizations A collection of organizations that should include all organizations the
   *     creator is affiliated with.
   * @return A NviCreator domain model including the full organization hierarchy for all
   *     affiliations.
   */
  public static NviCreator from(NviCreatorDto creator, Collection<Organization> organizations) {
    var creatorOrganizations = getCreatorOrganizations(creator.affiliations(), organizations);
    var verificationStatus =
        new VerificationStatus(
            creator instanceof VerifiedNviCreatorDto ? "Verified" : "NotVerified");
    var creatorId =
        creator instanceof VerifiedNviCreatorDto verifiedCreator ? verifiedCreator.id() : null;

    return new NviCreator(creatorId, creator.name(), verificationStatus, creatorOrganizations);
  }

  /**
   * Creates a NviCreator domain model from its database representation. The complete organization
   * hierarchy for a Candidate is stored as a separate field and not duplicated to each creator, and
   * must therefore be reconstructed separately here.
   *
   * @param creator A simplified database representation of a verified or unverified NVI Creator
   * @param organizations A collection of organizations that should include all organizations the
   *     creator is affiliated with.
   * @return A NviCreator domain model including the full organization hierarchy for all
   */
  public static NviCreator from(DbCreatorType creator, Collection<Organization> organizations) {
    var creatorOrganizations = getCreatorOrganizations(creator.affiliations(), organizations);
    var verificationStatus =
        (creator instanceof DbCreator)
            ? new VerificationStatus("Verified")
            : new VerificationStatus("NotVerified");
    var creatorId =
        (creator instanceof DbCreator verifiedCreator) ? verifiedCreator.creatorId() : null;

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
    var candidateOrganizations = new ArrayDeque<>(topLevelOrganizations);
    while (!candidateOrganizations.isEmpty()) {
      var organization = candidateOrganizations.pop();
      if (organization.id().equals(affiliationId)) {
        return organization;
      }
      if (nonNull(organization.hasPart()) && !organization.hasPart().isEmpty()) {
        candidateOrganizations.addAll(organization.hasPart());
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
