package no.sikt.nva.nvi.common.dto;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.test.TestUtils.randomInstitutionName;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import no.sikt.nva.nvi.common.model.Sector;
import no.unit.nva.clients.CustomerDto;

public final class CustomerDtoFixtures {

  private CustomerDtoFixtures() {}

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
