package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.rest.fetch.ReportStatusDto.StatusDto;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;

public class FetchReportStatusByPublicationIdHandler extends ApiGatewayHandler<Void, ReportStatusDto> {

    private static final String PATH_PARAM_PUBLICATION_ID = "publicationId";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public FetchReportStatusByPublicationIdHandler() {
        this(new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()));
    }

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
        var publicationId = getPublicationId(requestInfo);
        return attempt(() -> publicationId)
                   .map(identifier -> Candidate.fetchByPublicationId(() -> identifier, candidateRepository,
                                                                     periodRepository))
                   .map(ReportStatusDto::fromCandidate)
                   .orElse(failure -> handleFailure(failure, publicationId));
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, ReportStatusDto output) {
        return HTTP_OK;
    }

    private static ReportStatusDto handleFailure(Failure<ReportStatusDto> failure, URI publicationId)
        throws ApiGatewayException {
        if (failure.getException() instanceof CandidateNotFoundException) {
            return ReportStatusDto.builder()
                       .withPublicationId(publicationId)
                       .withStatus(StatusDto.NOT_CANDIDATE)
                       .build();
        } else {
            throw ExceptionMapper.map(failure);
        }
    }

    private URI getPublicationId(RequestInfo requestInfo) {
        return URI.create(
            URLDecoder.decode(requestInfo.getPathParameters().get(PATH_PARAM_PUBLICATION_ID), StandardCharsets.UTF_8));
    }
}
