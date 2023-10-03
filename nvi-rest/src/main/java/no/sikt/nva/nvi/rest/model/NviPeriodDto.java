package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.attempt.Try.attempt;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

public record NviPeriodDto(String publishingYear,
                           String startDate,
                           String reportingDate) {

    public static final String INVALID_DATE_FORMAT = "Invalid date format!";

    public static NviPeriodDto fromNviPeriod(DbNviPeriod period) {
        return new NviPeriodDto(period.publishingYear(),
                                period.startDate().toString(),
                                period.reportingDate().toString());
    }

    public DbNviPeriod toNviPeriod() {
        return DbNviPeriod.builder()
                   .publishingYear(publishingYear)
                   .startDate(toInstant(startDate))
                   .reportingDate(toInstant(reportingDate))
                   .build();
    }

    private Instant toInstant(String reportingDate) {
        return attempt(() -> Instant.parse(reportingDate))
                   .orElseThrow(failure -> new IllegalArgumentException(INVALID_DATE_FORMAT));
    }
}
