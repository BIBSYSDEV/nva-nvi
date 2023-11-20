package no.sikt.nva.nvi.events.model;

import java.net.URI;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public record PersistedResourceMessage(URI resourceFileUri) {

    @Override
    @JacocoGenerated
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (PersistedResourceMessage) obj;
        return Objects.equals(this.resourceFileUri, that.resourceFileUri);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return "PersistedResourceMessage[" +
               "resourceFileUri=" + resourceFileUri + ']';
    }
}
