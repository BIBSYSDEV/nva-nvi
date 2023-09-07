package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.attempt.Try.attempt;
import java.time.Instant;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import nva.commons.apigateway.exceptions.BadRequestException;

public record NviPeriodDto(String publishingYear,
                           String reportingDate) {

    public static final String INVALID_REPORTING_DATE_MESSAGE = "Reporting date has invalid format";

    public NviPeriod toNviPeriod() throws BadRequestException {
        return new NviPeriod.Builder()
                   .withPublishingYear(publishingYear)
                   .withReportingDate(toInstant(reportingDate))
                   .build();
    }

    private Instant toInstant(String reportingDate) throws BadRequestException {
        return attempt(() -> Instant.parse(reportingDate))
                   .orElseThrow(failure -> new BadRequestException(INVALID_REPORTING_DATE_MESSAGE));
    }

    public static NviPeriodDto fromNviPeriod(NviPeriod period) {
        return new NviPeriodDto(period.publishingYear(), period.reportingDate().toString());
    }

}
