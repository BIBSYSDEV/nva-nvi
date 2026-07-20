package no.sikt.nva.nvi.events.model;

import java.net.URI;
import no.sikt.nva.nvi.common.dto.CandidateType;

public record CandidateEvaluatedMessage(CandidateType candidate) {

  public URI publicationId() {
    return candidate.publicationId();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private CandidateType candidate;

    private Builder() {}

    public Builder withCandidateType(CandidateType candidate) {
      this.candidate = candidate;
      return this;
    }

    public CandidateEvaluatedMessage build() {
      return new CandidateEvaluatedMessage(candidate);
    }
  }
}
