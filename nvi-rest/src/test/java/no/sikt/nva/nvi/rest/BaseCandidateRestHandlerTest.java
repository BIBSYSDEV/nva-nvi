package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Base test class for handlers that return a CandidateDto. */
// This should cover common things between `FetchNviCandidateHandler`,
// `FetchNviCandidateByPublicationIdHandler`,
// `UpdateNviCandidateStatusHandler`, `UpsertAssigneeHandler`, `CreateNoteHandler` and
// `RemoveNoteHandler`.
// This is intended to cover common code for handlers that return a CandidateDto
public abstract class BaseCandidateRestHandlerTest extends LocalDynamoTest {
  protected static final Context CONTEXT = mock(Context.class);
  protected final DynamoDbClient localDynamo = initializeTestDatabase();
  protected ByteArrayOutputStream output;

  protected CandidateRepository candidateRepository;
  protected PeriodRepository periodRepository;

  @BeforeEach
  void commonSetup() {
    output = new ByteArrayOutputStream();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
  }
}
