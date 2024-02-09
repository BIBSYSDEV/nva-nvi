package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.net.URI;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeName(NviPeriodDto.NVI_PERIOD)
public record NviPeriodDto(URI id,
                           String publishingYear,
                           String startDate,
                           String reportingDate) {

    public static final String INVALID_DATE_FORMAT = "Invalid date format!";
    public static final String NVI_PERIOD = "NviPeriod";

    public static NviPeriodDto fromNviPeriod(DbNviPeriod period) {
        return new NviPeriodDto(period.id(),
                                period.publishingYear(),
                                period.startDate().toString(),
                                period.reportingDate().toString());
    }

    public DbNviPeriod toNviPeriod() {
        return DbNviPeriod.builder()
                   .id(id)
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
