package no.sikt.nva.nvi.rest.remove;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.CandidateResponse;
import no.sikt.nva.nvi.rest.model.CandidateResponseMapper;
import no.sikt.nva.nvi.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;

public class RemoveNoteHandler extends ApiGatewayHandler<Void, CandidateResponse> {

    public static final String PARAM_NOTE_IDENTIFIER = "noteIdentifier";
    private final NviService service;

    @JacocoGenerated
    public RemoveNoteHandler() {
        this(NviService.defaultNviService());
    }

    public RemoveNoteHandler(NviService service) {
        super(Void.class);
        this.service = service;
    }

    @Override
    protected CandidateResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        var username = RequestUtil.getUsername(requestInfo);
        var candidateIdentifier = requestInfo.getPathParameter(CANDIDATE_IDENTIFIER);
        var noteIdentifier = requestInfo.getPathParameter(PARAM_NOTE_IDENTIFIER);
        return attempt(() -> service.deleteNote(UUID.fromString(candidateIdentifier),
                                                UUID.fromString(noteIdentifier),
                                                username.getValue()))
                   .map(CandidateResponseMapper::fromCandidate)
                   .orElseThrow(handleFailure());
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CandidateResponse output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static Function<Failure<CandidateResponse>, ApiGatewayException> handleFailure() {
        return failure -> {
            var exception = failure.getException();
            if (exception instanceof NoSuchElementException) {
                return new NotFoundException(exception.getMessage());
            }
            if (exception instanceof IllegalArgumentException) {
                return new UnauthorizedException(exception.getMessage());
            }
            if (exception instanceof IllegalStateException) {
                return new BadRequestException(exception.getMessage());
            }
            return (ApiGatewayException) exception;
        };
    }
}
