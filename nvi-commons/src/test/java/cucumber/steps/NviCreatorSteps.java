package cucumber.steps;

import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static org.junit.Assert.assertEquals;

import cucumber.contexts.NviCreatorContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import org.junit.jupiter.api.Assertions;

public class NviCreatorSteps {

  private final NviCreatorContext creatorContext;

  public NviCreatorSteps(NviCreatorContext creatorContext) {
    this.creatorContext = creatorContext;
  }

  @Given("a contributor named {string}")
  public void aContributorNamed(String name) {
    creatorContext.setName(name);
  }

  @Given("the contributor is affiliated with {string}")
  public void theContributorIsAffiliatedWith(String string) {
    creatorContext.addAffiliation(randomUriWithSuffix(string));
  }

  @Given("we have a DTO for this contributor")
  public void weHaveADTOForThisContributor() {
    creatorContext.buildContributorDto();
  }

  @When("the DTO is converted to a DB entity")
  public void theDTOIsConvertedToADBEntity() {
    creatorContext.buildDbEntity();
  }

  @Then("the DB entity should have the name {string}")
  public void theDBEntityShouldHaveTheName(String name) {
    var dbEntity = (DbUnverifiedCreator) creatorContext.getDbEntity();
    assertEquals(name, dbEntity.creatorName());
  }

  @Then("the DB entity should have the same affiliations as the DTO")
  public void theDBEntityShouldHaveTheAffiliationsAnd() {
    var dtoAffiliations = creatorContext.getDto().affiliations();
    var dbEntityAffiliations = creatorContext.getDbEntity().affiliations();
    assertEquals(dtoAffiliations, dbEntityAffiliations);
  }

  @Then("all the other stuff")
  public void otherChecks() {
    var originalDto = creatorContext.getDto();
    var dbEntity = (DbUnverifiedCreator) creatorContext.getDbEntity();
    var roundTrippedCreator = dbEntity.copy().toNviCreator();
    Assertions.assertEquals(originalDto, roundTrippedCreator);

    var dbCreator1 =
        DbUnverifiedCreator.builder()
            .creatorName(creatorContext.getName())
            .affiliations(creatorContext.getDto().affiliations())
            .build();
    var dbCreator2 = ((UnverifiedNviCreatorDto) originalDto).toDao();
    Assertions.assertEquals(dbCreator1, dbCreator2);
  }
}
