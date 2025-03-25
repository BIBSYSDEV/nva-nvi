package no.sikt.nva.nvi.common.examples;

import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.client.model.Organization;

/**
 * Example models for testing purposes, corresponding to the data in
 * /resources/expandedPublications/
 */
public class ExampleOrganizations {

  public static final URI SIKT_ID =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  public static final URI SIKT_SUBUNIT_ID =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.2.0.0");
  public static final URI NTNU_ID =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.0.0.0");

  public static final Map<String, String> SIKT_LABELS =
      Map.of(
          "nb",
          "Sikt – Kunnskapssektorens tjenesteleverandør",
          "en",
          "Sikt - Norwegian Agency for Shared Services in Education and Research");

  public static final Map<String, String> SIKT_SUBUNIT_LABELS =
      Map.of(
          "nn", "Divisjon for forskings- og kunnskapsressursar",
          "nb", "Divisjon forsknings- og kunnskapsressurser",
          "en", "The Research and Education Resources Division");

  public static final Map<String, String> NTNU_LABELS =
      Map.of(
          "nn", "Noregs teknisk-naturvitskaplege universitet",
          "nb", "Norges teknisk-naturvitenskapelige universitet",
          "en", "Norwegian University of Science and Technology");

  private static final String TYPE_ORGANIZATION = "Organization";
  public static final Organization TOP_LEVEL_ORGANIZATION_NTNU =
      Organization.builder()
          .withId(NTNU_ID)
          .withLabels(NTNU_LABELS)
          .withType(TYPE_ORGANIZATION)
          .build();

  public static final Organization TOP_LEVEL_ORGANIZATION_SIKT =
      Organization.builder()
          .withId(SIKT_ID)
          .withLabels(SIKT_LABELS)
          .withType(TYPE_ORGANIZATION)
          .withHasPart(
              List.of(
                  Organization.builder()
                      .withId(SIKT_SUBUNIT_ID)
                      .withLabels(SIKT_SUBUNIT_LABELS)
                      .withType(TYPE_ORGANIZATION)
                      .withPartOf(List.of(Organization.builder().withId(SIKT_ID).build()))
                      .build()))
          .build();

  public static final Organization SUB_ORGANIZATION_SIKT =
      Organization.builder()
          .withId(SIKT_SUBUNIT_ID)
          .withLabels(SIKT_SUBUNIT_LABELS)
          .withType(TYPE_ORGANIZATION)
          .withPartOf(
              List.of(
                  Organization.builder()
                      .withId(SIKT_ID)
                      .withLabels(SIKT_LABELS)
                      .withType(TYPE_ORGANIZATION)
                      .withHasPart(List.of(Organization.builder().withId(SIKT_SUBUNIT_ID).build()))
                      .build()))
          .build();

  public static final Organization EXAMPLE_TOP_LEVEL_ORGANIZATION_3 =
      Organization.builder()
          .withId(
              URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/35900068.0.0.0"))
          .withType(TYPE_ORGANIZATION)
          .withLabels(Map.of("nb", "University of Cape Town"))
          .build();
}
