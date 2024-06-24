package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.exceptions.NotApplicableException;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.core.JacocoGenerated;

public class CreateNoteHandler extends ApiGatewayHandler<NviNoteRequest, CandidateDto> {

    public static final String INVALID_REQUEST_ERROR = "Request body must contain text field.";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public CreateNoteHandler() {
        this(new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()));
    }

    public CreateNoteHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository) {
        super(NviNoteRequest.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    protected void validateRequest(NviNoteRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
        validate(input);
    }

    @Override
    protected CandidateDto processInput(NviNoteRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        var username = RequestUtil.getUsername(requestInfo);
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
        var institutionId = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        return attempt(() -> Candidate.fetch(() -> candidateIdentifier, candidateRepository, periodRepository))
                   .map(this::checkIfApplicable)
                   .map(candidate -> candidate.createNote(new CreateNoteRequest(input.text(), username.value(),
                                                                                institutionId)))
                   .map(Candidate::toDto)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviNoteRequest input, CandidateDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    private Candidate checkIfApplicable(Candidate candidate) {
        if (candidate.isApplicable()) {
            return candidate;
        }
        throw new NotApplicableException();
    }

    private void validate(NviNoteRequest input) throws BadRequestException {
        if (Objects.isNull(input.text())) {
            throw new BadRequestException(INVALID_REQUEST_ERROR);
        }
    }
}
