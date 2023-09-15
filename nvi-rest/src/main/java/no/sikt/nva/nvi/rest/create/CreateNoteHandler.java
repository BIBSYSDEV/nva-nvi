package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
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
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class CreateNoteHandler extends ApiGatewayHandler<NviNoteRequest, CandidateResponse> {

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
        validateRequest(requestInfo);
        var username = RequestUtil.getUsername(requestInfo);
        var candidateIdentifier = requestInfo.getPathParameter(PARAM_CANDIDATE_IDENTIFIER);

        var candidate = service.createNote(UUID.fromString(candidateIdentifier), getNote(input, username));
        return CandidateResponse.fromCandidate(candidate, service);
    }

    private void validateRequest(RequestInfo requestInfo)
        throws UnauthorizedException, BadRequestException, NotFoundException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        RequestUtil.validatePeriod(requestInfo, service);
    }

    @Override
    protected Integer getSuccessStatusCode(NviNoteRequest input, CandidateResponse output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static DbNote getNote(NviNoteRequest input, DbUsername username) {
        return DbNote.builder()
                   .text(input.note())
                   .user(new DbUsername(username.value()))
                   .build();
    }
}
