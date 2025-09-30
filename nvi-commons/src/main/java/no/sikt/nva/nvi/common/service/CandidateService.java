package no.sikt.nva.nvi.common.service;

import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@SuppressWarnings("PMD.UnusedPrivateField")
public class CandidateService {
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;

  public CandidateService(DynamoDbClient client) {
    candidateRepository = new CandidateRepository(client);
    periodRepository = new PeriodRepository(client);
  }
}
