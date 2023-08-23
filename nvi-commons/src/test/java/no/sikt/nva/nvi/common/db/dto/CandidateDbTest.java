package no.sikt.nva.nvi.common.db.dto;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.Test;

public class CandidateDbTest {
    @Test
    void shouldNotLooseDataWhenCopied() throws JsonProcessingException {
        var original = TestUtils.randomCandidate().toDb();
        var copy = original.copy().build();

        var originalJson = objectMapper.valueToTree(original);
        var copyJson = objectMapper.valueToTree(copy);
        assertEquals(originalJson, copyJson);
    }

    @Test
    void shouldNotAlterCopyWhenMakingChangesToOriginal() throws JsonProcessingException {
        var original = TestUtils.randomCandidate().toDb();
        var copy = original.copy().build();

        original.setPublicationDate(null);

        var originalJson = objectMapper.valueToTree(original);
        var copyJson = objectMapper.valueToTree(copy);
        assertNotEquals(originalJson, copyJson);
    }
}