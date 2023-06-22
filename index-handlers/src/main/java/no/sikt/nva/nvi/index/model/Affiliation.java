package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record Affiliation(@JsonProperty(ID) String id,
                          @JsonProperty(LABELS) Map<String, String> labels,
                          @JsonProperty(APPROVAL_STATUS) String approvalStatus) {

    public static final String ID = "id";
    public static final String LABELS = "labels";
    public static final String APPROVAL_STATUS = "approvalStatus";
}
