package no.sikt.nva.nvi.events.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.InputStream;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.ioutils.IoUtils;

public record RecoveryEvent(int numberOfMessageToProcess) implements JsonSerializable {

  public static RecoveryEvent fromInputStream(InputStream inputStream)
      throws JsonProcessingException {
    var value = IoUtils.streamToString(inputStream);
    return JsonUtils.dtoObjectMapper.readValue(value, RecoveryEvent.class);
  }
}
