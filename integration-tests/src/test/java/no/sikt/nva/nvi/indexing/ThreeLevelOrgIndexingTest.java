package no.sikt.nva.nvi.indexing;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationHierarchy;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationWithNestedPartOf;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import cucumber.contexts.EvaluationContext;
import cucumber.contexts.IndexingContext;
import java.util.List;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a single org hierarchy definition feeds both paths: the publication (so evaluation
 * rolls a section-level affiliation up to the institution) and the org registry (so the index
 * document's affiliation carries the full nested partOf chain). The registry response is built with
 * {@code createOrganizationWithNestedPartOf} because the generator walks partOf within one
 * document.
 */
class ThreeLevelOrgIndexingTest {

  @Test
  void shouldIndexAffiliationWithFullPartOfChainFromTheRegistry() {
    var scenario = new TestScenario();
    var evaluationContext = new EvaluationContext(scenario);
    var indexingContext = new IndexingContext(scenario);

    var institutionId = randomOrganizationId();
    var departmentId = randomOrganizationId();
    var sectionId = randomOrganizationId();
    var institution = createOrganizationHierarchy(institutionId, departmentId, sectionId);
    var section = institution.hasPart().get(0).hasPart().get(0);

    indexingContext.registerOrganization(
        createOrganizationWithNestedPartOf(sectionId, departmentId, institutionId));

    var factory =
        new SampleExpandedPublicationFactory(List.of(createCustomer(institutionId, true)));
    factory.withTopLevelOrganizations(List.of(institution)).withCreatorAffiliatedWith(section);
    evaluationContext.mockGetAllCustomersResponse(factory.getCustomerOrganizations());
    setupOpenPeriod(scenario, THIS_YEAR);
    evaluationContext.evaluatePublicationAndPersistResult(factory.getExpandedPublication());
    var candidate = scenario.getCandidateByPublicationId(factory.getPublicationId());

    indexingContext.index(candidate);
    var document = indexingContext.readIndexDocument(candidate).indexDocument();

    var indexedAffiliation =
        document.publicationDetails().nviContributors().stream()
            .flatMap(contributor -> contributor.affiliations().stream())
            .filter(NviOrganization.class::isInstance)
            .map(NviOrganization.class::cast)
            .findFirst()
            .orElseThrow();

    assertThat(indexedAffiliation.id()).isEqualTo(sectionId);
    assertThat(indexedAffiliation.partOf()).containsExactlyInAnyOrder(departmentId, institutionId);
  }
}
