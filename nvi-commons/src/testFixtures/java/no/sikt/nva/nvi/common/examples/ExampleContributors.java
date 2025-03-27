package no.sikt.nva.nvi.common.examples;

import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.EXAMPLE_TOP_LEVEL_ORGANIZATION_3;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.SUB_ORGANIZATION_SIKT;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_NTNU;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_SIKT;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.etl.ContributorDto;
import no.sikt.nva.nvi.common.etl.ContributorRole;
import no.sikt.nva.nvi.common.etl.VerificationStatus;

/**
 * Example models for testing purposes, corresponding to the data in
 * /resources/expandedPublications/
 */
public class ExampleContributors {

  private static final ContributorRole ROLE_CREATOR = new ContributorRole("Creator");
  private static final ContributorRole ROLE_OTHER = new ContributorRole("ContactPerson");
  private static final VerificationStatus STATUS_VERIFIED = new VerificationStatus("Verified");
  private static final VerificationStatus STATUS_UNVERIFIED = new VerificationStatus("NotVerified");

  public static final ContributorDto EXAMPLE_1_CONTRIBUTOR =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1215176"))
          .withName("Ola Nordmann")
          .withRole(ROLE_CREATOR)
          .withVerificationStatus(STATUS_VERIFIED)
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_1 =
      ContributorDto.builder()
          .withName("Petter Smart")
          .withRole(ROLE_CREATOR)
          .withVerificationStatus(STATUS_UNVERIFIED)
          .withAffiliations(List.of(TOP_LEVEL_ORGANIZATION_NTNU))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_2 =
      ContributorDto.builder()
          .withName("John Doe")
          .withRole(ROLE_CREATOR)
          .withVerificationStatus(STATUS_UNVERIFIED)
          .withAffiliations(List.of(EXAMPLE_TOP_LEVEL_ORGANIZATION_3))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_3 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1685065"))
          .withName("Donald Duck")
          .withRole(ROLE_CREATOR)
          .withVerificationStatus(STATUS_VERIFIED)
          .withAffiliations(List.of(TOP_LEVEL_ORGANIZATION_SIKT))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_4 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1685046"))
          .withName("Skrue McDuck")
          .withRole(ROLE_OTHER)
          .withVerificationStatus(STATUS_VERIFIED)
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_5 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1215176"))
          .withName("Ola Nordmann")
          .withRole(ROLE_CREATOR)
          .withVerificationStatus(STATUS_VERIFIED)
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
          .build();
}
