package no.sikt.nva.nvi.index.model;

import java.util.UUID;

public record ConsumptionAttributes(UUID documentIdentifier, String index) {

    public static final String INDEX_NAME = "nvi-candidates";

    public static ConsumptionAttributes from(UUID documentIdentifier) {
        return new ConsumptionAttributes(documentIdentifier, INDEX_NAME);
    }

}
