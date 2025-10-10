package no.sikt.nva.nvi.common.db;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.scanDB;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.model.ResponseContext;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.service.CandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

class CandidateRepositoryTest {

  private DynamoDbClient localDynamo;
  private CandidateRepository candidateRepository;
  private CandidateService candidateService;

  @BeforeEach
  void setUp() {
    var scenario = new TestScenario();
    localDynamo = scenario.getLocalDynamo();
    candidateRepository = scenario.getCandidateRepository();
    candidateService = scenario.getCandidateService();
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
    candidateService.upsert(originalRequest);
    var candidateDao =
        candidateRepository.findByPublicationId(originalRequest.publicationId()).get();
    var originalDbCandidate = candidateDao.candidate();

    var newUpsertRequest = requestBuilder.withInstanceType(InstanceType.ACADEMIC_MONOGRAPH).build();
    candidateService.upsert(newUpsertRequest);
    var updatedDbCandidate =
        candidateRepository.findCandidateById(candidateDao.identifier()).get().candidate();

    assertThat(scanDB(localDynamo).count(), is(equalTo(3)));
    assertThat(updatedDbCandidate, is(not(equalTo(originalDbCandidate))));
  }

  @Test
  void shouldThrowTransactionExceptionWhenFailingOnSendingTransaction() {
    var client = mock(DynamoDbClient.class);
    var failingRepository = spy(new CandidateRepository(client));

    doReturn(new ResponseContext(Optional.empty(), emptyList()))
        .when(failingRepository)
        .getCandidateAggregate((URI) any());

    when(client.transactWriteItems((TransactWriteItemsRequest) any()))
        .thenThrow(getTransactionCanceledException());

    var candidate = randomCandidateBuilder(true).build();
    var exception =
        assertThrows(
            TransactionException.class, () -> failingRepository.create(candidate, List.of()));

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
