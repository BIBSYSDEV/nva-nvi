package no.sikt.nva.nvi.indexing;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import cucumber.contexts.EvaluationContext;
import java.util.List;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import org.junit.jupiter.api.Test;

/**
 * Spike confirming that a creator affiliated with a third-level organization (institution ->
 * department -> section) is rolled up to the top-level institution during evaluation. This is the
 * assumption behind building the org hierarchy once via the factory: the evaluator resolves the
 * institution from the merged RDF graph (partOf* transitive closure), so a flat per-affiliation
 * partOf is enough as long as the full hierarchy is present in topLevelOrganizations.
 */
class ThreeLevelOrgEvaluationRollupTest {

  @Test
  void shouldRollSectionAffiliationUpToTheInstitution() {
    var scenario = new TestScenario();
    var evaluationContext = new EvaluationContext(scenario);

    var institutionId = randomOrganizationId();
    var departmentId = randomOrganizationId();
    var sectionId = randomOrganizationId();
    var institution = createOrganizationHierarchy(institutionId, departmentId, sectionId);
    var section = institution.hasPart().get(0).hasPart().get(0);

    var factory =
        new SampleExpandedPublicationFactory(List.of(createCustomer(institutionId, true)));
    factory.withTopLevelOrganizations(List.of(institution)).withCreatorAffiliatedWith(section);

    evaluationContext.mockGetAllCustomersResponse(factory.getCustomerOrganizations());
    setupOpenPeriod(scenario, THIS_YEAR);
    evaluationContext.evaluatePublicationAndPersistResult(factory.getExpandedPublication());

    var candidate = scenario.getCandidateByPublicationId(factory.getPublicationId());

    assertThat(candidate.approvals()).containsOnlyKeys(institutionId);
    assertThat(candidate.getInstitutionPoints(institutionId)).isPresent();
  }
}
