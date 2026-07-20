package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;
import static nva.commons.core.StringUtils.isBlank;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;
import no.sikt.nva.nvi.common.dto.VerificationStatus;
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
    nviAffiliations = copyOfNullable(nviAffiliations);
    topLevelNviOrganizations = copyOfNullable(topLevelNviOrganizations);
    shouldNotBeNull(verificationStatus, "Required field 'verificationStatus' is null");
    if (isBlank(name)) {
      shouldNotBeNull(id, "Both 'id' and 'name' is null, one of these fields must be set");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder copy() {
    return builder()
        .withId(id)
        .withName(name)
        .withVerificationStatus(verificationStatus)
        .withNviAffiliations(nviAffiliations)
        .withTopLevelNviOrganizations(topLevelNviOrganizations);
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

  public Set<URI> getAffiliationIds() {
    return Set.copyOf(nviAffiliations);
  }

  public Set<URI> getNviAffiliationsPartOf(URI institutionId) {
    return topLevelNviOrganizations.stream()
        .filter(organization -> organization.id().equals(institutionId))
        .flatMap(
            organization -> nviAffiliations.stream().filter(organization::isTopLevelOrganizationOf))
        .collect(Collectors.toSet());
  }

  /**
   * The set of ancestor organizations for one of this creator's affiliations, resolved from the
   * persisted NVI organization hierarchy. An empty {@link Optional} means the affiliation is not
   * found in the tree; an empty set means the affiliation is itself a top-level organization.
   */
  public Optional<Set<URI>> findAncestorsOf(URI affiliationId) {
    return topLevelNviOrganizations.stream()
        .map(organization -> organization.findAncestorsOf(affiliationId))
        .flatMap(Optional::stream)
        .findFirst();
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
    LOGGER.error(
        "Failed to find top-level organization for {}, which indicates incomplete organization tree"
            + " in persisted data.",
        affiliationId);
    return null;
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

  public static final class Builder {
    private URI id;
    private String name;
    private VerificationStatus verificationStatus;
    private Collection<URI> nviAffiliations;
    private Collection<Organization> topLevelNviOrganizations;

    public Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withVerificationStatus(VerificationStatus verificationStatus) {
      this.verificationStatus = verificationStatus;
      return this;
    }

    public Builder withNviAffiliations(Collection<URI> nviAffiliations) {
      this.nviAffiliations = nviAffiliations;
      return this;
    }

    public Builder withTopLevelNviOrganizations(Collection<Organization> topLevelNviOrganizations) {
      this.topLevelNviOrganizations = topLevelNviOrganizations;
      return this;
    }

    public NviCreator build() {
      return new NviCreator(
          id, name, verificationStatus, nviAffiliations, topLevelNviOrganizations);
    }
  }
}
