package no.sikt.nva.nvi.events.batch.message;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import no.sikt.nva.nvi.events.batch.request.BatchJobType;

public record CandidateJobMessage(UUID candidateIdentifier, BatchJobType jobType)
    implements BatchJobMessage {

  public CandidateJobMessage {
    requireNonNull(candidateIdentifier, "candidateIdentifier must not be null");
    requireNonNull(jobType, "jobType must not be null");
  }
}
