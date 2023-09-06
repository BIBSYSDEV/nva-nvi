package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.CandidateResponse;
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
    public CreateNotesHandler(){
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
        return null;
    }

    @Override
    protected Integer getSuccessStatusCode(NviNotesRequest input, CandidateResponse output) {
        return null;
    }
}
