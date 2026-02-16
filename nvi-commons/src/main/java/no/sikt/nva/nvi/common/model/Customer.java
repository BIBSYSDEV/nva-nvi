package no.sikt.nva.nvi.common.model;

import java.net.URI;
import no.unit.nva.clients.CustomerDto;

public record Customer(URI id, URI cristinId, boolean nviInstitution, String sector) {

  public static Customer fromCustomerDto(CustomerDto customerDto) {
    return new Customer(
        customerDto.id(),
        customerDto.cristinId(),
        customerDto.nviInstitution(),
        customerDto.sector());
  }
}
