package no.sikt.nva.nvi.events.batch;

import static java.util.Objects.isNull;
import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.events.model.ReEvaluateRequest;
import no.unit.nva.events.handlers.EventHandler;
import no.unit.nva.events.models.AwsEventBridgeEvent;

public class ReEvaluateNviCandidatesHandler extends EventHandler<ReEvaluateRequest, Void> {

    public static final String INVALID_INPUT_MSG = "Invalid request. Field year is required";

    public ReEvaluateNviCandidatesHandler() {
        super(ReEvaluateRequest.class);
    }

    @Override
    protected Void processInput(ReEvaluateRequest input, AwsEventBridgeEvent<ReEvaluateRequest> event,
                                Context context) {
        validateInput(input);
        return null;
    }

    private void validateInput(ReEvaluateRequest input) {
        if (isNull(input) || isNull(input.year())) {
            throw new IllegalArgumentException(INVALID_INPUT_MSG);
        }
    }
}
