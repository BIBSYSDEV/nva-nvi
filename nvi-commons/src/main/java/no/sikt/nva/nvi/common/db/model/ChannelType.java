package no.sikt.nva.nvi.common.db.model;

import java.util.Arrays;

public enum ChannelType {

    JOURNAL("Journal"),
    SERIES("Series"),
    PUBLISHER("Publisher");

    private final String value;

    ChannelType(String value) {
        this.value = value;
    }

    public static ChannelType parse(String value) {
        return Arrays.stream(values())
                   .filter(type -> type.getValue().equalsIgnoreCase(value))
                   .findFirst()
                   .orElse(null);
    }

    public String getValue() {
        return value;
    }
}
