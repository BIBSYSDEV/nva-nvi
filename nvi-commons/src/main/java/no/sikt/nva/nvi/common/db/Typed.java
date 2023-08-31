package no.sikt.nva.nvi.common.db;

import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public interface Typed {

    String TYPE_FIELD = "type";

    String getType();

    default void setType(String type) {
        if (!getType().equals(type)) {
            throw new IllegalStateException(errorMessage(type));
        }
    }

    private String errorMessage(String type) {
        return String.format("Unexpected type: %s.Expected type: %s", type, getType());
    }
}
