package no.sikt.nva.nvi.index.model.search;

import java.util.Arrays;

public enum OrderByFields {
    CREATED_DATE("createdDate");

    private final String value;

    OrderByFields(String value) {
        this.value = value;
    }

    public static OrderByFields parse(String value) {
        return Arrays.stream(OrderByFields.values())
                   .filter(field -> field.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElseThrow(() -> new IllegalArgumentException("Invalid OrderByField: " + value));
    }

    public String getValue() {
        return value;
    }
}
