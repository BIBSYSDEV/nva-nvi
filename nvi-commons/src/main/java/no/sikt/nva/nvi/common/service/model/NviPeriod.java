package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import nva.commons.apigateway.exceptions.BadRequestException;

public class NviPeriod {

    private final URI expectedId;
    private final Integer publishingYear;
    private final Instant startDate;
    private final Instant reportingDate;

    protected NviPeriod(URI id, Integer publishingYear, Instant startDate, Instant reportingDate) {
        this.expectedId = id;
        this.publishingYear = publishingYear;
        this.startDate = startDate;
        this.reportingDate = reportingDate;
    }

    public static NviPeriod upsert(UpsertPeriodRequest request, PeriodRepository periodRepository)
        throws IllegalArgumentException {
        request.validate();
        URI id = constructId();
        var dbPeriod = DbNviPeriod.builder()
                           .id(id)
                           .publishingYear(String.valueOf(request.publishingYear()))
                           .startDate(request.startDate())
                           .reportingDate(request.reportingDate())
                           .build();
        periodRepository.save(dbPeriod);
        return NviPeriod.fetch(id, periodRepository);
    }

    public static List<NviPeriod> fetchAll(PeriodRepository periodRepository) {
        return null;
    }

    private static URI constructId() {
        return URI.create("");
    }

    private static NviPeriod fetch(URI id, PeriodRepository periodRepository) {
        return null;
    }
}
