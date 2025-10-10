package no.sikt.nva.nvi.common.db.model;

import static nva.commons.core.StringUtils.isBlank;

import java.util.Collection;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

/** Wrapper class for data typically fetched at the same time for a single candidate. */
public record ResponseContext(
    Optional<CandidateAggregate> candidateAggregate, Collection<NviPeriodDao> allPeriods) {

  public Optional<NviPeriod> getOptionalPeriod(String year) {
    if (isBlank(year)) {
      throw new IllegalArgumentException("Year cannot be blank");
    }
    return allPeriods.stream()
        .filter(period -> year.equals(period.nviPeriod().publishingYear()))
        .map(NviPeriod::fromDao)
        .findFirst();
  }
}
