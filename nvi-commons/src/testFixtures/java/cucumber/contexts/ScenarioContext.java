package cucumber.contexts;

import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;

public class ScenarioContext {
  private final TestScenario scenario;

  public ScenarioContext(TestScenario scenario) {
    this.scenario = scenario;
  }

  public CandidateRepository getCandidateRepository() {
    return scenario.getCandidateRepository();
  }

  public PeriodRepository getPeriodRepository() {
    return scenario.getPeriodRepository();
  }
}
