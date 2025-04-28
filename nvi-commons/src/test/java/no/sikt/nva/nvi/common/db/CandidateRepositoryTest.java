package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.scanDB;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class CandidateRepositoryTest {

  private DynamoDbClient localDynamo;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;

  @BeforeEach
  void setUp() {
    localDynamo = initializeTestDatabase();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = new PeriodRepository(localDynamo);
  }

  @Test
  void shouldThrowExceptionWhenAttemptingToSaveCandidateWithExistingPublicationId() {
    var publicationId = randomUri();
    var candidate1 = randomCandidateBuilder(true).publicationId(publicationId).build();
    var candidate2 = randomCandidateBuilder(true).publicationId(publicationId).build();
    candidateRepository.create(candidate1, List.of());
    assertThrows(RuntimeException.class, () -> candidateRepository.create(candidate2, List.of()));
    assertThat(scanDB(localDynamo).count(), is(equalTo(2)));
  }

  @Test
  void shouldOverwriteExistingCandidateWhenUpdating() {
    var requestBuilder =
        createUpsertCandidateRequest(randomUri()).withInstanceType(InstanceType.ACADEMIC_ARTICLE);
    var originalRequest = requestBuilder.build();
    Candidate.upsert(originalRequest, candidateRepository, periodRepository);
    var candidateDao =
        candidateRepository.findByPublicationId(originalRequest.publicationId()).get();
    var originalDbCandidate = candidateDao.candidate();

    var newUpsertRequest = requestBuilder.withInstanceType(InstanceType.ACADEMIC_MONOGRAPH).build();
    Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository);
    var updatedDbCandidate =
        candidateRepository.findCandidateById(candidateDao.identifier()).get().candidate();

    assertThat(scanDB(localDynamo).count(), is(equalTo(3)));
    assertThat(updatedDbCandidate, is(not(equalTo(originalDbCandidate))));
  }
}
