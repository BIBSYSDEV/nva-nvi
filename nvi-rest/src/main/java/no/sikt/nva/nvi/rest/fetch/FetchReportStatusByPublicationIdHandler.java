package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public class FetchReportStatusByPublicationIdHandler extends ApiGatewayHandler<Void, ReportStatusDto> {

    private static final String CANDIDATE_PUBLICATION_ID = "publicationId";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    public FetchReportStatusByPublicationIdHandler(CandidateRepository candidateRepository,
                                                   PeriodRepository periodRepository) {
        super(Void.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {

    }

    @Override
    protected ReportStatusDto processInput(Void unused, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        return attempt(() -> getPublicationId(requestInfo))
                   .map(identifier -> Candidate.fetchByPublicationId(() -> identifier, candidateRepository,
                                                                     periodRepository))
                   .map(ReportStatusDto::fromCandidate)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, ReportStatusDto output) {
        return HTTP_OK;
    }

    private URI getPublicationId(RequestInfo requestInfo) {
        return URI.create(
            URLDecoder.decode(requestInfo.getPathParameters().get(CANDIDATE_PUBLICATION_ID), StandardCharsets.UTF_8));
    }
}
