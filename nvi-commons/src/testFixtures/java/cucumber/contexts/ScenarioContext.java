package cucumber.contexts;

import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.unit.nva.stubs.FakeS3Client;
import org.picocontainer.Startable;

public class ScenarioContext implements Startable {
  private TestScenario scenario;
  private FakeS3Client s3Client;

  public ScenarioContext(TestScenario scenario) {
    this.scenario = scenario;
  }

  public CandidateRepository getCandidateRepository() {
    return scenario.getCandidateRepository();
  }

  public PeriodRepository getPeriodRepository() {
    return scenario.getPeriodRepository();
  }

  public FakeS3Client getS3Client() {
    return s3Client;
  }

  @Override
  public void start() {
    this.scenario = new TestScenario();
    this.s3Client = new FakeS3Client();
  }

  @Override
  public void stop() {
    s3Client.close();
  }
}
