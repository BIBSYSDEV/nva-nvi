package handlers;

import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.model.dto.EvaluatedCandidateDto;
import no.sikt.nva.nvi.common.service.NviService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpsertNviCandidateHandler.class);
    private final NviService nviService;

    public UpsertNviCandidateHandler(NviService nviService) {
        this.nviService = nviService;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::parseBody)
            .filter(Objects::nonNull)
            .map(this::validate)
            .filter(Objects::nonNull)
            .forEach(this::upsertNviCandidate);

        return null;
    }

    private void upsertNviCandidate(EvaluatedCandidateDto evaluatedCandidate) {
        nviService.upsertCandidate(evaluatedCandidate);
    }

    private EvaluatedCandidateDto parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, EvaluatedCandidateDto.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private EvaluatedCandidateDto validate(EvaluatedCandidateDto request) {
        if (isNull(request.publicationBucketUri())) {
            logInvalidMessageBody(request.toString());
            return null;
        }
        return request;
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error("Message body invalid: {}", body);
    }
}
