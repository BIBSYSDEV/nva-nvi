package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.Test;

class GraphUtilsTest {

  @Test
  void shouldNotThrowDuringCreateModel() {
    var modelStr = IoUtils.stringFromResources(Path.of("candidate.json"));
    assertDoesNotThrow(() -> createModel(dtoObjectMapper.readTree(modelStr)));
  }
}
