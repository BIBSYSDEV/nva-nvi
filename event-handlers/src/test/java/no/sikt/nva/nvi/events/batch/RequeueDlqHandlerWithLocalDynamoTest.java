package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateWithYear;
import static no.sikt.nva.nvi.events.batch.RequeueDlqTestUtils.generateMessages;
import static no.sikt.nva.nvi.events.batch.RequeueDlqTestUtils.setupSqsClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.LocalDynamoTestSetup;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class RequeueDlqHandlerWithLocalDynamoTest extends LocalDynamoTestSetup {

  public static final Context CONTEXT = mock(Context.class);
  public static final int YEAR = 2021;
  private static final String DLQ_URL = "https://some-sqs-url";
  private SqsClient sqsClient;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;
  private NviQueueClient client;

  @BeforeEach
  void setUp() {
    sqsClient = setupSqsClient();
    client = new NviQueueClient(sqsClient);
    var localDynamoDbClient = initializeTestDatabase();
    candidateRepository = new CandidateRepository(localDynamoDbClient);
    periodRepository = PeriodRepositoryFixtures.periodRepositoryReturningOpenedPeriod(YEAR);
  }

  @Test
  void shouldRequeueCandidateWithoutLossOfInformation() {
    var expectedCandidate =
        createCandidateDao(candidateRepository, randomCandidateWithYear(String.valueOf(YEAR)));
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(
            ReceiveMessageResponse.builder()
                .messages(generateMessages(1, "firstBatch", expectedCandidate.identifier()))
                .build());

    var handler = new RequeueDlqHandler(client, DLQ_URL, candidateRepository, periodRepository);
    handler.handleRequest(new RequeueDlqInput(1), CONTEXT);
    var actualCandidate =
        candidateRepository.findCandidateById(expectedCandidate.identifier()).orElseThrow();
    assertEquals(expectedCandidate.identifier(), actualCandidate.identifier());
    assertEquals(expectedCandidate.candidate(), actualCandidate.candidate());
  }
}
