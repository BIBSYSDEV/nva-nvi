package no.sikt.nva.nvi.index.model.document;

import static org.junit.Assert.assertThrows;
import no.sikt.nva.nvi.index.model.document.PublicationChannel.ScientificValue;
import org.junit.jupiter.api.Test;

class PublicationChannelTest {

    @Test
    void shouldThrowIllegalArgumentExceptionWhenParsingUnknownValue() {
        var unknownValue = "UnknownValue";
        assertThrows(IllegalArgumentException.class, () -> ScientificValue.parse(unknownValue));
    }
}