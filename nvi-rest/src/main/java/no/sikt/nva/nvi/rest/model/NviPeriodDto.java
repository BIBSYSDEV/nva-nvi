package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.attempt.Try.attempt;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

public record NviPeriodDto(String publishingYear,
                           String reportingDate) {

    public static final String INVALID_REPORTING_DATE_MESSAGE = "Invalid reporting date";

    public static NviPeriodDto fromNviPeriod(DbNviPeriod period) {
        return new NviPeriodDto(period.publishingYear(), period.reportingDate().toString());
    }

    public DbNviPeriod toNviPeriod() {
        return DbNviPeriod.builder()
                   .publishingYear(publishingYear)
                   .reportingDate(toInstant(reportingDate))
                   .build();
    }

    private Instant toInstant(String reportingDate) {
        return attempt(() -> Instant.parse(reportingDate))
                   .orElseThrow(failure -> new IllegalArgumentException(INVALID_REPORTING_DATE_MESSAGE));
    }
}
