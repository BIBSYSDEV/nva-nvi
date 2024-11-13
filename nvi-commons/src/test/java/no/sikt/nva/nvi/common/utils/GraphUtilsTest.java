package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.common.utils.GraphUtils.isNviCandidate;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.Test;

class GraphUtilsTest {

    @Test
    public void shouldNotThrowDuringCreateModel() {
        var modelStr = IoUtils.stringFromResources(Path.of("candidate.json"));
        assertDoesNotThrow(() -> createModel(dtoObjectMapper.readTree(modelStr)));
    }

    @Test
    public void shouldReturnFalseWhenIsCandidateIsCalledOnNonCandidate() throws JsonProcessingException {
        var modelStr = IoUtils.stringFromResources(Path.of("candidate.json"));
        var model = createModel(dtoObjectMapper.readTree(modelStr));
        assertFalse(isNviCandidate(model));
    }

}