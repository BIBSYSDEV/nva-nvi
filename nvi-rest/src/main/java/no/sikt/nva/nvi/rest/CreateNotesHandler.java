package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.time.Instant;
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
import nva.commons.core.JacocoGenerated;

public class CreateNotesHandler extends ApiGatewayHandler<NviNotesRequest, CandidateResponse> {

    private final NviService service;

    @JacocoGenerated
    public CreateNotesHandler() {
        this(new NviService(defaultDynamoClient()));
    }

    public CreateNotesHandler(NviService service) {
        super(NviNotesRequest.class);
        this.service = service;
    }

    @Override
    protected CandidateResponse processInput(NviNotesRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        var username = RequestUtil.getUsername(requestInfo);
        var candidateIdentifier = requestInfo.getPathParameter(PARAM_CANDIDATE_IDENTIFIER);

        var candidate = service.createNote(UUID.fromString(candidateIdentifier), getNote(input, username));
        return CandidateResponse.fromCandidate(candidate);
    }

    @Override
    protected Integer getSuccessStatusCode(NviNotesRequest input, CandidateResponse output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static DbNote getNote(NviNotesRequest input, DbUsername username) {
        return DbNote.builder()
                   .noteId(UUID.randomUUID())
                   .text(input.note())
                   .user(new DbUsername(username.value()))
                   .createdDate(Instant.now())
                   .build();
    }
}
