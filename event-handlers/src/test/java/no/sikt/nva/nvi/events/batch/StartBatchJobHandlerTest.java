package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.List;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.batch.model.BatchJobType;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import no.unit.nva.stubs.FakeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// FIXME: Temporary suppression to split up PRs
@SuppressWarnings("PMD.SingularField")
class StartBatchJobHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private StartBatchJobHandler handler;
  private FakeSqsClient queueClient;
  private CandidateService candidateService;
  private TestScenario scenario;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    queueClient = new FakeSqsClient();
    handler = new StartBatchJobHandler(candidateService, queueClient);
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void placeholderTestThatShouldFail() {
    var request =
        new StartBatchJobRequest(
            BatchJobType.REFRESH_CANDIDATES,
            new ReportingYearFilter(List.of(String.valueOf(CURRENT_YEAR))),
            100,
            5,
            null);
    assertThatThrownBy(() -> handler.handleRequest(request, CONTEXT))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
