package no.sikt.nva.nvi.common.service;

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
                   .filter(NviPeriod::isClosed)
                   .map(NviPeriod::getPublishingYear)
                   .reduce(Integer::max);
    }
}
