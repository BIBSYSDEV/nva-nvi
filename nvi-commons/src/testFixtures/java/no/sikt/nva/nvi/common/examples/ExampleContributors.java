package no.sikt.nva.nvi.common.examples;

import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.EXAMPLE_TOP_LEVEL_ORGANIZATION_3;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.SUB_ORGANIZATION_SIKT;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_NTNU;
import static no.sikt.nva.nvi.common.examples.ExampleOrganizations.TOP_LEVEL_ORGANIZATION_SIKT;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.etl.ContributorDto;

/**
 * Example models for testing purposes, corresponding to the data in
 * /resources/expandedPublications/
 */
public class ExampleContributors {

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_3 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1685065"))
          .withName("Donald Duck")
          .withRole("Creator")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(TOP_LEVEL_ORGANIZATION_SIKT))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_1 =
      ContributorDto.builder()
          .withName("Petter Smart")
          .withRole("Creator")
          .withVerificationStatus("NotVerified")
          .withAffiliations(List.of(TOP_LEVEL_ORGANIZATION_NTNU))
          .build();

  public static final ContributorDto EXAMPLE_1_CONTRIBUTOR =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1215176"))
          .withName("Ola Nordmann")
          .withRole("Creator")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_4 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1685046"))
          .withName("Skrue McDuck")
          .withRole("ContactPerson")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_5 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1215176"))
          .withName("Ola Nordmann")
          .withRole("Creator")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
          .build();

  public static final ContributorDto EXAMPLE_2_CONTRIBUTOR_2 =
      ContributorDto.builder()
          .withName("John Doe")
          .withRole("Creator")
          .withVerificationStatus("NotVerified")
          .withAffiliations(List.of(EXAMPLE_TOP_LEVEL_ORGANIZATION_3))
          .build();
}
