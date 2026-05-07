package no.sikt.nva.nvi.index.apigateway;

import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import org.mockito.ArgumentMatcher;

public class CandidateSearchParamsAffiliationMatcher
    implements ArgumentMatcher<CandidateSearchParameters> {

  private final CandidateSearchParameters source;

  public CandidateSearchParamsAffiliationMatcher(CandidateSearchParameters source) {
    this.source = source;
  }

  @Override
  public boolean matches(CandidateSearchParameters other) {
    return other.affiliationIdentifiers().equals(source.affiliationIdentifiers());
  }
}
