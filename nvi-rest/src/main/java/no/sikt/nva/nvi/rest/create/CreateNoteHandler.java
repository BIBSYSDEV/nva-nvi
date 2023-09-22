package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.core.JacocoGenerated;

public class CreateNoteHandler extends ApiGatewayHandler<NviNoteRequest, CandidateResponse> {

    public static final String INVALID_REQUEST_ERROR = "Request body must contain text field.";
    private final NviService service;

    @JacocoGenerated
    public CreateNoteHandler() {
        this(new NviService(defaultDynamoClient()));
    }

    public CreateNoteHandler(NviService service) {
        super(NviNoteRequest.class);
        this.service = service;
    }

    @Override
    protected CandidateResponse processInput(NviNoteRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        var username = RequestUtil.getUsername(requestInfo);
        var candidateIdentifier = requestInfo.getPathParameter(CANDIDATE_IDENTIFIER);

        validate(input);
        var candidate = service.createNote(UUID.fromString(candidateIdentifier), getNote(input, username));
        return CandidateResponse.fromCandidate(candidate);
    }

    @Override
    protected Integer getSuccessStatusCode(NviNoteRequest input, CandidateResponse output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static DbNote getNote(NviNoteRequest input, DbUsername username) {
        return DbNote.builder()
                   .text(input.text())
                   .user(DbUsername.fromString(username.getValue()))
                   .build();
    }

    private void validate(NviNoteRequest input) throws BadRequestException {
        if (Objects.isNull(input.text())) {
            throw new BadRequestException(INVALID_REQUEST_ERROR);
        }
    }
}
