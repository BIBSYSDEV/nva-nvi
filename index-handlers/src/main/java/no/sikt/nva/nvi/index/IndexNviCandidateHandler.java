package no.sikt.nva.nvi.index;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import no.sikt.nva.nvi.index.model.NviCandidateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid: {}";
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexNviCandidateHandler.class);

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords().forEach(sqsMessage -> {
            var body = sqsMessage.getBody();
            var message = extractMessage(body);
        });
        return null;
    }

    private NviCandidateMessage extractMessage(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, NviCandidateMessage.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error(ERROR_MESSAGE_BODY_INVALID, body);
    }
}
