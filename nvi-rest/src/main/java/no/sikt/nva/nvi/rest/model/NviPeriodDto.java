package no.sikt.nva.nvi.rest.model;

import java.time.Instant;
import no.sikt.nva.nvi.common.model.business.NviPeriod;

public record NviPeriodDto(String publishingYear,
                           Instant reportingDate) {

    public NviPeriod toNviPeriod() {
        return new NviPeriod.Builder()
                   .withPublishingYear(publishingYear)
                   .withReportingDate(reportingDate)
                   .build();
    }

    public static NviPeriodDto fromNviPeriod(NviPeriod period) {
        return new NviPeriodDto(period.publishingYear(), period.reportingDate());
    }

}
