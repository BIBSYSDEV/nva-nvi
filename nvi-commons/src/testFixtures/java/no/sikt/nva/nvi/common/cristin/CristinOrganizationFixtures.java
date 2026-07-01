package no.sikt.nva.nvi.common.cristin;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.randomEnglishUnitLabel;
import static no.sikt.nva.nvi.test.TestUtils.randomNorwegianUnitLabel;
import static no.sikt.nva.nvi.test.TestUtils.randomUnitName;
import static no.unit.nva.testutils.RandomDataGenerator.FAKER;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import nva.commons.core.JacocoGenerated;

/** Fixtures to generate fake responses from Cristin proxy API (/cristin/organization). */
public final class CristinOrganizationFixtures {

  private static final String ORGANIZATION_CONTEXT =
      "https://bibsysdev.github.io/src/organization-context.json";

  private CristinOrganizationFixtures() {}

  public static FakeCristinOrganization.Builder randomCristinOrganization(URI organizationId) {
    var unitName = randomUnitName();
    var labels =
        Map.of("nb", randomNorwegianUnitLabel(unitName), "en", randomEnglishUnitLabel(unitName));
    return FakeCristinOrganization.builder()
        .withId(organizationId)
        .withAcronym(FAKER.word().noun().toUpperCase(Locale.ROOT))
        .withCountryCode(COUNTRY_CODE_NORWAY)
        .withContext(ORGANIZATION_CONTEXT)
        .withType("Organization")
        .withLabels(labels);
  }

  // TODO: Ignored in test coverage temporarily
  @JacocoGenerated
  public static FakeCristinOrganization.Builder randomCristinOrganization(
      URI organizationId, int numberOfSubOrganizations) {
    var selfReferentialLeafNode = FakeCristinOrganization.asLeafNode(organizationId);
    var subOrganizations =
        IntStream.range(0, numberOfSubOrganizations)
            .mapToObj(
                i ->
                    randomCristinOrganization(randomOrganizationId())
                        .withPartOf(List.of(selfReferentialLeafNode)))
            .map(FakeCristinOrganization.Builder::build)
            .toList();

    return randomCristinOrganization(organizationId).withHasPart(subOrganizations);
  }

  /**
   * Builds a Cristin organization response as a nested partOf chain, from the given node up to the
   * root. The index document generator fetches this per NVI affiliation and walks partOf within the
   * single returned document, so the whole chain must be embedded (it does not re-fetch parents).
   * The first id is the node itself, the last is the top-level institution.
   */
  public static FakeCristinOrganization organizationWithNestedPartOf(URI... idsFromNodeToRoot) {
    FakeCristinOrganization organization = null;
    for (var index = idsFromNodeToRoot.length - 1; index >= 0; index--) {
      var builder = randomCristinOrganization(idsFromNodeToRoot[index]);
      if (nonNull(organization)) {
        builder.withPartOf(List.of(organization));
      }
      organization = builder.build();
    }
    return organization;
  }
}
