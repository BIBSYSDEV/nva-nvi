package no.sikt.nva.nvi.index.model.document;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;

import java.util.UUID;

public record ConsumptionAttributes(UUID documentIdentifier, String index) {

  public static ConsumptionAttributes from(UUID documentIdentifier) {
    return new ConsumptionAttributes(documentIdentifier, NVI_CANDIDATES_INDEX);
  }
}
