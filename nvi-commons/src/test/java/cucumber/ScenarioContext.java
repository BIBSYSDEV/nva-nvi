package cucumber;

import no.sikt.nva.nvi.common.service.model.Candidate;

public class ScenarioContext {

  private Candidate candidate;
  private String exampleName;
  private int potato;

  public ScenarioContext() {}

  public String getExampleName() {
    return exampleName;
  }

  public void setExampleName(String exampleName) {
    this.exampleName = exampleName;
  }

  public Candidate getCandidate() {
    return candidate;
  }

  public void setCandidate(Candidate candidate) {
    this.candidate = candidate;
  }

  public int getPotato() {
    return potato;
  }

  public void setPotato(int potato) {
    this.potato = potato;
  }
}
