package no.sikt.nva.nvi.events.cristin;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

class CristinMapperTest {

    @Test
    void shouldThrowNullPointerExceptionWhenQualityCodeIsMissing() {
        var empty = emptyScientificResource();
        var build = CristinNviReport.builder().withScientificResources(List.of(empty)).build();
        assertThrows(NullPointerException.class, () -> CristinMapper.toDbCandidate(build));
    }

    private ScientificResource emptyScientificResource() {
        return ScientificResource.build().build();
    }
}