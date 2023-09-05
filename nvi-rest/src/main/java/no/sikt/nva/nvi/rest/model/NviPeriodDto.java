package no.sikt.nva.nvi.rest.model;

import java.time.Instant;
import no.sikt.nva.nvi.common.model.business.DbNviPeriod;

public record NviPeriodDto(String publishingYear,
                           Instant reportingDate) {

    public DbNviPeriod toNviPeriod() {
        return DbNviPeriod.builder()
                   .publishingYear(publishingYear)
                   .reportingDate(reportingDate)
                   .build();
    }

    public static NviPeriodDto fromNviPeriod(DbNviPeriod period) {
        return new NviPeriodDto(period.publishingYear(), period.reportingDate());
    }

}
