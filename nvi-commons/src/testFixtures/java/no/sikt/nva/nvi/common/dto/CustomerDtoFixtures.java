package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.test.TestUtils.randomInstitutionName;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.model.Sector;
import no.unit.nva.clients.CustomerDto;

public final class CustomerDtoFixtures {

  // Example URIs based on static test data.
  private static final URI EXAMPLE_NVI_CUSTOMER_ORG =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
  private static final URI EXAMPLE_NON_NVI_CUSTOMER_ORG =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/150.0.0.0");
  private static final URI SIKT_CRISTIN_ORG_ID =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");

  private CustomerDtoFixtures() {}

  public static List<CustomerDto> getDefaultCustomers() {
    return List.of(
        createNviCustomer(SIKT_CRISTIN_ORG_ID),
        createNviCustomer(EXAMPLE_NVI_CUSTOMER_ORG),
        createNonNviCustomer(EXAMPLE_NON_NVI_CUSTOMER_ORG));
  }

  public static CustomerDto createNviCustomer(URI organizationId) {
    return createCustomer(organizationId, true);
  }

  public static CustomerDto createNonNviCustomer(URI organizationId) {
    return createCustomer(organizationId, false);
  }

  public static CustomerDto createCustomer(URI organizationId, boolean isNviInstitution) {
    return new CustomerDto(
        randomUri(),
        null,
        null,
        randomInstitutionName(),
        null,
        organizationId,
        null,
        isNviInstitution,
        false,
        false,
        emptyList(),
        null,
        false,
        Sector.UNKNOWN.toString());
  }
}
