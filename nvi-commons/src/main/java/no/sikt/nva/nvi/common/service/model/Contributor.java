package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.model.DbContributor;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.dto.ContributorRole;
import no.sikt.nva.nvi.common.dto.VerificationStatus;

public record Contributor(
    URI id,
    String name,
    VerificationStatus verificationStatus,
    ContributorRole role,
    List<URI> affiliations,
    List<Organization> topLevelOrganizations) {

  public static Contributor from(ContributorDto dtoContributor) {
    var affiliations = dtoContributor.affiliations().stream().map(Organization::id).toList();
    var contributorOrganizations =
        dtoContributor.affiliations().stream().map(Organization::getTopLevelOrg).toList();

    return new Contributor(
        dtoContributor.id(),
        dtoContributor.name(),
        dtoContributor.verificationStatus(),
        dtoContributor.role(),
        affiliations,
        contributorOrganizations);
  }

  public static Contributor from(
      List<Organization> topLevelOrganizationTree, DbContributor dbContributor) {
    var topLevelOrganizationsMap =
        topLevelOrganizationTree.stream().collect(Collectors.toMap(Organization::id, org -> org));
    var contributorOrganizations =
        dbContributor.topLevelOrganizations().stream().map(topLevelOrganizationsMap::get).toList();

    return new Contributor(
        dbContributor.id(),
        dbContributor.name(),
        new VerificationStatus(dbContributor.verificationStatus()),
        new ContributorRole(dbContributor.role()),
        dbContributor.affiliations(),
        contributorOrganizations);
  }

  public DbContributor toDbContributor() {
    return DbContributor.builder()
        .id(id)
        .name(name)
        .verificationStatus(
            nonNull(verificationStatus)
                ? verificationStatus.getValue()
                : "Unverified") // TODO: Handle null verification status
        .role(nonNull(role) ? role.getValue() : "Unknown") // TODO: Handle null role
        .affiliations(affiliations)
        .topLevelOrganizations(topLevelOrganizations.stream().map(Organization::id).toList())
        .build();
  }

  public ContributorDto toDto() {
    var affiliationDtos =
        affiliations.stream()
            .map(affiliation -> Organization.builder().withId(affiliation).build())
            .collect(Collectors.toList());
    return ContributorDto.builder()
        .withId(id)
        .withName(name)
        .withVerificationStatus(verificationStatus)
        .withRole(role)
        .withAffiliations(affiliationDtos)
        //                                  .withTopLevelOrganizations(topLevelOrganizations) //
        // TODO
        .build();
  }
}
