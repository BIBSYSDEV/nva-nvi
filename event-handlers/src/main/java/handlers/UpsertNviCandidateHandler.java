package handlers;

import static java.util.Objects.isNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import handlers.model.UpsertRequest;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import no.sikt.nva.nvi.common.service.NviCandidateService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpsertNviCandidateHandler.class);

    private static final Environment ENVIRONMENT = new Environment();
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");

    private static final String PUBLICATION_API_PATH = ENVIRONMENT.readEnv("PUBLICATION_API_PATH");

    private final NviCandidateService nviCandidateService;

    public UpsertNviCandidateHandler(NviCandidateService nviCandidateService) {
        this.nviCandidateService = nviCandidateService;
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

    private static List<URI> mapToUris(List<String> uriStrings) {
        return uriStrings.stream()
                   .map(URI::create)
                   .toList();
    }

    @JacocoGenerated
    //TODO: Remove JacocoGenerated when following test is implemented: shouldUpdateCandidateIfExists
    private void upsertNviCandidate(UpsertRequest request) {
        var publicationId = toPublicationId(request.publicationBucketUri());
        if (isNotExistingCandidate(publicationId)) {
            nviCandidateService.createCandidateWithPendingInstitutionApprovals(publicationId,
                                                                               mapToUris(
                                                                                   request.approvalAffiliations()));
        }
    }

    private boolean isNotExistingCandidate(URI publicationId) {
        return !nviCandidateService.exists(publicationId);
    }

    private URI toPublicationId(String publicationBucketUri) {
        var identifier = UriWrapper.fromUri(publicationBucketUri).getLastPathElement();
        return UriWrapper.fromHost(API_HOST).addChild(PUBLICATION_API_PATH).addChild(identifier).getUri();
    }

    private UpsertRequest parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, UpsertRequest.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private UpsertRequest validate(UpsertRequest request) {
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
