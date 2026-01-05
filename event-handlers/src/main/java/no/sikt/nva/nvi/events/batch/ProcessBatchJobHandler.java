package no.sikt.nva.nvi.events.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import nva.commons.core.JacocoGenerated;

public class ProcessBatchJobHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  @JacocoGenerated
  public ProcessBatchJobHandler() {}

  @Override
  public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
