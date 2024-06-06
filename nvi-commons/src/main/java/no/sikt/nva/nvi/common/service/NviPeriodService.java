package no.sikt.nva.nvi.common.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

public class NviPeriodService {

    private final PeriodRepository periodRepository;

    public NviPeriodService(PeriodRepository periodRepository) {
        this.periodRepository = periodRepository;
    }

    public List<NviPeriod> fetchAll() {
        return periodRepository.getPeriods().stream().map(NviPeriod::fromDbObject).toList();
    }

    public Optional<Integer> fetchLatestClosedPeriodYear() {
        return periodRepository.getPeriods().stream()
                   .map(NviPeriod::fromDbObject)
                   .map(NviPeriod::getReportingDate)
                   .filter(NviPeriodService::isBeforeNow)
                   .map(NviPeriodService::getYear)
                   .reduce(Integer::max);
    }

    private static int getYear(Instant reportingDate) {
        return reportingDate.atZone(ZoneId.systemDefault()).getYear();
    }

    private static boolean isBeforeNow(Instant reportingDate) {
        return reportingDate.isBefore(Instant.now());
    }
}
