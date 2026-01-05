package no.sikt.nva.nvi.events.batch;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.InputStream;
import java.io.OutputStream;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import nva.commons.core.JacocoGenerated;

public class StartBatchJobHandler implements RequestStreamHandler {

  @JacocoGenerated
  public StartBatchJobHandler() {}

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @SuppressWarnings("unused")
  private StartBatchJobRequest parseRequest(InputStream input) throws Exception {
    return dtoObjectMapper.readValue(input, StartBatchJobRequest.class);
  }
}
