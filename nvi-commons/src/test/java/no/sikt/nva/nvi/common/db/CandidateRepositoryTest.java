package no.sikt.nva.nvi.common.db;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.randomApplicableCandidateDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.CandidateService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

class CandidateRepositoryTest {

  private CandidateRepository candidateRepository;
  private CandidateService candidateService;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    setupOpenPeriod(scenario, CURRENT_YEAR);
    candidateRepository = scenario.getCandidateRepository();
    candidateService = scenario.getCandidateService();
  }

  @Test
  void shouldThrowExceptionWhenAttemptingToSaveCandidateWithExistingPublicationId() {
    var publicationId = randomUri();
    var candidate1 =
        createCandidateDao(randomCandidateBuilder(true).publicationId(publicationId).build());
    var candidate2 =
        createCandidateDao(randomCandidateBuilder(true).publicationId(publicationId).build());

    candidateRepository.create(candidate1, emptyList());
    assertThrows(RuntimeException.class, () -> candidateRepository.create(candidate2, emptyList()));
  }

  @Test
  void shouldOverwriteExistingCandidateWhenUpdating() {
    var requestBuilder =
        createUpsertCandidateRequest(randomUri()).withInstanceType(InstanceType.ACADEMIC_ARTICLE);
    var originalRequest = requestBuilder.build();
    candidateService.upsertCandidate(originalRequest);
    var candidateIdentifier =
        candidateRepository.findByPublicationId(originalRequest.publicationId()).orElseThrow();
    var candidateDao = candidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
    var originalDbCandidate = candidateDao.candidate();

    var newUpsertRequest = requestBuilder.withInstanceType(InstanceType.ACADEMIC_MONOGRAPH).build();
    candidateService.upsertCandidate(newUpsertRequest);
    var updatedDbCandidate =
        candidateRepository.findCandidateById(candidateDao.identifier()).get().candidate();

    Assertions.assertThat(updatedDbCandidate).isNotEqualTo(originalDbCandidate);

    var candidatesInDb = candidateRepository.scanEntries(500, null, List.of(KeyField.CANDIDATE));
    Assertions.assertThat(candidatesInDb.getDatabaseEntries()).hasSize(1);
  }

  @Test
  void shouldThrowTransactionExceptionWhenFailingOnSendingTransaction() {
    var client = mock(DynamoDbClient.class);
    var failingRepository = new CandidateRepository(client);

    when(client.transactWriteItems((TransactWriteItemsRequest) any()))
        .thenThrow(getTransactionCanceledException());

    var exception =
        assertThrows(
            TransactionException.class,
            () -> failingRepository.create(randomApplicableCandidateDao(), emptyList()));

    assertTrue(exception.getMessage().contains("Operation PUT with condition"));
  }

  private static TransactionCanceledException getTransactionCanceledException() {
    return TransactionCanceledException.builder()
        .cancellationReasons(
            List.of(
                CancellationReason.builder()
                    .code(randomString())
                    .item(
                        Map.of(randomString(), AttributeValue.builder().s(randomString()).build()))
                    .message(randomString())
                    .build()))
        .build();
  }
}
