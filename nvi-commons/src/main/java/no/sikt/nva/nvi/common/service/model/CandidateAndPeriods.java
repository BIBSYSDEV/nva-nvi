package no.sikt.nva.nvi.common.service.model;

import static nva.commons.core.StringUtils.isBlank;

import java.util.Collection;
import java.util.Optional;

/**
 * Holds an existing candidate (if any) along with all NVI periods. Used when evaluating
 * publications to compare the candidate's current period against the period matching the
 * publication's year.
 */
public record CandidateAndPeriods(Candidate candidate, Collection<NviPeriod> allPeriods) {

  public Optional<Candidate> getCandidate() {
    return Optional.ofNullable(candidate);
  }

  public Optional<NviPeriod> getPeriod(String year) {
    if (isBlank(year)) {
      throw new IllegalArgumentException("Year cannot be blank");
    }
    return getPeriod(Integer.parseInt(year));
  }

  public Optional<NviPeriod> getPeriod(int year) {
    return allPeriods.stream().filter(period -> period.publishingYear().equals(year)).findFirst();
  }
}
