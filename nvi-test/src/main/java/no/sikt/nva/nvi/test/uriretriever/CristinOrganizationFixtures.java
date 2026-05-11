package no.sikt.nva.nvi.test.uriretriever;

import static no.unit.nva.testutils.RandomDataGenerator.FAKER;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

// FIXME: Ignoring in test coverage temporarily
@JacocoGenerated
public final class CristinOrganizationFixtures {

  private static final String DEFAULT_API_HOST = "api.dev.nva.aws.unit.no";
  private static final String COUNTRY_CODE_NORWAY = "NO";
  private static final String ORGANIZATION_CONTEXT =
      "https://bibsysdev.github.io/src/organization-context.json";

  private CristinOrganizationFixtures() {}

  public static URI organizationIdFromIdentifier(String identifier) {
    return UriWrapper.fromHost(DEFAULT_API_HOST)
        .addChild("cristin")
        .addChild("organization")
        .addChild(identifier)
        .getUri();
  }

  public static String randomAcademicUnit() {
    var baseUnits = List.of("University of", "Department of", "Institute of", "Section for");
    var base = FAKER.options().option(baseUnits.toArray(String[]::new));
    return String.join(" ", base, FAKER.word().adjective(), FAKER.word().noun());
  }

  public static URI randomOrganizationId() {
    var identifier = FAKER.numerify("###.###.###.###");
    return organizationIdFromIdentifier(identifier);
  }

  public static FakeCristinOrganization.Builder randomCristinOrganization(URI organizationId) {
    var label = randomAcademicUnit();
    return FakeCristinOrganization.builder()
        .withId(organizationId)
        .withAcronym(FAKER.word().noun().toUpperCase(Locale.ROOT))
        .withCountryCode(COUNTRY_CODE_NORWAY)
        .withContext(ORGANIZATION_CONTEXT)
        .withType("Organization")
        .withLabels(Map.of("nb", label, "en", label));
  }

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
}
