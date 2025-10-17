package no.sikt.nva.nvi.common.service.model;

import static nva.commons.core.StringUtils.isBlank;

import java.util.Collection;
import java.util.Optional;

public record CandidateContext(Optional<Candidate> candidate, Collection<NviPeriod> allPeriods) {

  public Optional<NviPeriod> getOptionalPeriod(String year) {
    if (isBlank(year)) {
      throw new IllegalArgumentException("Year cannot be blank");
    }
    return getOptionalPeriod(Integer.parseInt(year));
  }

  public Optional<NviPeriod> getOptionalPeriod(int year) {
    return allPeriods.stream().filter(period -> period.publishingYear().equals(year)).findFirst();
  }
}
