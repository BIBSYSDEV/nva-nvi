package no.sikt.nva.nvi.common.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;
import no.sikt.nva.nvi.common.dto.VerificationStatus;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @param topLevelNviOrganizations A collection of top-level organizations that the person is
 *     affiliated with, either directly or indirectly.
 */
public record NviCreator(
    URI id,
    String name,
    VerificationStatus verificationStatus,
    Collection<URI> nviAffiliations,
    Collection<Organization> topLevelNviOrganizations) {

  private static final Logger LOGGER = LoggerFactory.getLogger(NviCreator.class);

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
    var affiliatedTopLevelOrganizations =
        findTopLevelOrganizations(creator.affiliations(), organizations);
    var verificationStatus =
        new VerificationStatus(
            creator instanceof VerifiedNviCreatorDto ? "Verified" : "NotVerified");
    var creatorId =
        creator instanceof VerifiedNviCreatorDto verifiedCreator ? verifiedCreator.id() : null;

    return new NviCreator(
        creatorId,
        creator.name(),
        verificationStatus,
        creator.affiliations(),
        affiliatedTopLevelOrganizations);
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
    var affiliatedTopLevelOrganizations =
        findTopLevelOrganizations(creator.affiliations(), organizations);
    var verificationStatus =
        (creator instanceof DbCreator)
            ? new VerificationStatus("Verified")
            : new VerificationStatus("NotVerified");
    var creatorId =
        (creator instanceof DbCreator verifiedCreator) ? verifiedCreator.creatorId() : null;

    return new NviCreator(
        creatorId,
        creator.creatorName(),
        verificationStatus,
        creator.affiliations(),
        affiliatedTopLevelOrganizations);
  }

  public List<URI> getAffiliationIds() {
    return List.copyOf(nviAffiliations);
  }

  public static Predicate<NviCreator> isAffiliatedWithTopLevelOrganization(
      URI topLevelOrganizationId) {
    return creator ->
        creator.topLevelNviOrganizations().stream()
            .map(Organization::id)
            .anyMatch(id -> id.equals(topLevelOrganizationId));
  }

  private static List<Organization> findTopLevelOrganizations(
      Collection<URI> affiliations, Collection<Organization> topLevelOrganizations) {

    return affiliations.stream()
        .map(id -> findTopLevelOrganizationOf(id, topLevelOrganizations))
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private static Organization findTopLevelOrganizationOf(
      URI affiliationId, Collection<Organization> topLevelOrganizations) {
    var topLevelOrganization =
        topLevelOrganizations.stream()
            .filter(organization -> organization.isTopLevelOrganizationOf(affiliationId))
            .findFirst();
    if (topLevelOrganization.isPresent()) {
      return topLevelOrganization.get();
    }
    LOGGER.warn("Failed to find top-level organization for {}", affiliationId);
    return null;
  }

  public NviCreatorDto toDto() {
    if (isVerified()) {
      return new VerifiedNviCreatorDto(id, name, List.copyOf(nviAffiliations));
    }
    return new UnverifiedNviCreatorDto(name, List.copyOf(nviAffiliations));
  }

  public boolean isVerified() {
    return nonNull(id) && verificationStatus.isVerified();
  }

  public DbCreatorType toDbCreatorType() {
    if (isVerified()) {
      return new DbCreator(id, name, List.copyOf(nviAffiliations));
    }
    return new DbUnverifiedCreator(name, List.copyOf(nviAffiliations));
  }
}
