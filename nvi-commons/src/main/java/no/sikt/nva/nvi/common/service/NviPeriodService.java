package no.sikt.nva.nvi.common.service;

import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

public class NviPeriodService {

  private final PeriodRepository periodRepository;

  public NviPeriodService(PeriodRepository periodRepository) {
    this.periodRepository = periodRepository;
  }

  public void save(NviPeriod nviPeriod) {}

  // FIXME: Use this
  public NviPeriod fetchByPublishingYear(String publishingYear) {
    return periodRepository
        .findByPublishingYearAsDao(publishingYear)
        .map(NviPeriod::fromDbPeriod)
        .orElseThrow(
            () ->
                PeriodNotFoundException.withMessage(
                    String.format("Period for year %s does not exist!", publishingYear)));
  }

  public List<NviPeriod> fetchAll() {
    return periodRepository.getPeriods().stream().map(NviPeriod::fromDbPeriod).toList();
  }

  public Optional<Integer> fetchLatestClosedPeriodYear() {
    return periodRepository.getPeriods().stream()
        .map(NviPeriod::fromDbPeriod)
        .filter(NviPeriod::isClosed)
        .map(NviPeriod::getPublishingYear)
        .reduce(Integer::max);
  }
}
