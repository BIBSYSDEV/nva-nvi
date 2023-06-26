package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record Affiliation(@JsonProperty(ID) String id,
                          @JsonProperty(LABELS) Map<String, String> labels,
                          @JsonProperty(APPROVAL_STATUS) String approvalStatus) {

    private static final String ID = "id";
    private static final String LABELS = "labels";
    private static final String APPROVAL_STATUS = "approvalStatus";
}
