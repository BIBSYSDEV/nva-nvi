package cucumber.steps;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import cucumber.contexts.EvaluationContext;
import cucumber.contexts.IndexingContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.test.SampleExpandedContributor;
import no.sikt.nva.nvi.test.SampleExpandedPublication;

public class IndexingSteps {

  private final TestScenario scenario;
  private final EvaluationContext evaluationContext;
  private final IndexingContext indexingContext;

  private SampleExpandedPublicationFactory publicationFactory;
  private Organization institution;
  private Organization firstDepartment;
  private Organization secondDepartment;
  private ContributorDto creator;
  private Candidate candidate;
  private NviCandidateIndexDocument initialDocument;
  private NviCandidateIndexDocument updatedDocument;

  public IndexingSteps(TestScenario scenario) {
    this.scenario = scenario;
    this.evaluationContext = new EvaluationContext(scenario);
    this.indexingContext = new IndexingContext(scenario);
  }

  @Given("an NVI institution with two departments")
  public void anNviInstitutionWithTwoDepartments() {
    publicationFactory = new SampleExpandedPublicationFactory();
    institution = publicationFactory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    firstDepartment = institution.hasPart().get(0);
    secondDepartment = institution.hasPart().get(1);
    indexingContext.registerOrganizationPartOf(firstDepartment.id(), institution.id());
    indexingContext.registerOrganizationPartOf(secondDepartment.id(), institution.id());
  }

  @Given("a reported Candidate whose creator is affiliated with the first department")
  public void aReportedCandidateWhoseCreatorIsAffiliatedWithTheFirstDepartment() {
    creator = verifiedCreatorFrom(firstDepartment);
    publicationFactory.withContributor(creator);
    evaluationContext.mockGetAllCustomersResponse(publicationFactory.getCustomerOrganizations());

    setupOpenPeriod(scenario, THIS_YEAR);
    evaluationContext.evaluatePublicationAndPersistResult(publicationFactory.getExpandedPublication());

    candidate = scenario.getCandidateByPublicationId(publicationFactory.getPublicationId());
    for (var institutionId : candidate.approvals().keySet()) {
      scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED, institutionId);
    }
    setupClosedPeriod(scenario, THIS_YEAR);
    scenario.getCandidateService().reportCandidate(candidate.identifier(), Instant.now());

    candidate = scenario.getCandidateByIdentifier(candidate.identifier());
    assertThat(candidate.isReported()).isTrue();
  }

  @Given("the Candidate has been indexed")
  public void theCandidateHasBeenIndexed() {
    indexingContext.index(candidate);
    initialDocument = indexingContext.readIndexDocument(candidate).indexDocument();
  }

  @When("the Candidate is indexed")
  public void theCandidateIsIndexed() {
    indexingContext.index(candidate);
    updatedDocument = indexingContext.readIndexDocument(candidate).indexDocument();
  }

  @When("the creator is moved to the second department in the source publication")
  public void theCreatorIsMovedToTheSecondDepartmentInTheSourcePublication() {
    indexingContext.overwriteSource(candidate, publicationWithCreatorAffiliatedTo(secondDepartment));
  }


  @Then("the indexed NVI affiliations match the Candidate")
  public void theIndexedNviAffiliationsMatchTheCandidate() {
    assertThat(indexedNviAffiliations(updatedDocument)).isEqualTo(candidateAffiliations());
  }

  @Then("the indexed NVI affiliations are unchanged")
  public void theIndexedNviAffiliationsAreUnchanged() {
    assertThat(indexedNviAffiliations(updatedDocument))
        .isEqualTo(indexedNviAffiliations(initialDocument));
  }

  @Then("the indexed NVI points match the Candidate")
  public void theIndexedNviPointsMatchTheCandidate() {
    assertThat(indexedInstitutionPoints(updatedDocument)).isEqualTo(candidateInstitutionPoints());
    assertThat(updatedDocument.points()).isEqualByComparingTo(candidate.getTotalPoints());
  }

  @Then("the indexed NVI points are unchanged")
  public void theIndexedNviPointsAreUnchanged() {
    assertThat(indexedInstitutionPoints(updatedDocument))
        .isEqualTo(indexedInstitutionPoints(initialDocument));
    assertThat(updatedDocument.points()).isEqualByComparingTo(initialDocument.points());
  }

  @Then("the indexed NVI creators match the Candidate")
  public void theIndexedNviCreatorsMatchTheCandidate() {
    assertThat(indexedCreators(updatedDocument)).isEqualTo(candidateCreators());
  }

  @Then("the indexed NVI creators are unchanged")
  public void theIndexedNviCreatorsAreUnchanged() {
    assertThat(indexedCreators(updatedDocument)).isEqualTo(indexedCreators(initialDocument));
  }

  private SampleExpandedPublication publicationWithCreatorAffiliatedTo(Organization affiliation) {
    var movedCreator =
        SampleExpandedContributor.builder()
            .withId(creator.id())
            .withNames(List.of(creator.name()))
            .withRole("Creator")
            .withVerificationStatus("Verified")
            .withOrcId(randomUri())
            .withAffiliations(
                List.of(SampleExpandedPublicationFactory.mapOrganizationToAffiliation(affiliation)))
            .build();
    return publicationFactory
        .getExpandedPublicationBuilder()
        .withContributors(List.of(movedCreator))
        .build();
  }

  private static Set<URI> indexedNviAffiliations(NviCandidateIndexDocument document) {
    return document.publicationDetails().nviContributors().stream()
        .map(NviContributor::affiliations)
        .flatMap(List::stream)
        .filter(NviOrganization.class::isInstance)
        .map(NviOrganization.class::cast)
        .map(NviOrganization::id)
        .collect(Collectors.toSet());
  }

  private Set<URI> candidateAffiliations() {
    var publicationDetails = candidate.publicationDetails();
    return Stream.concat(
            publicationDetails.verifiedCreators().stream()
                .flatMap(creator -> creator.affiliations().stream()),
            publicationDetails.unverifiedCreators().stream()
                .flatMap(creator -> creator.affiliations().stream()))
        .collect(Collectors.toSet());
  }

  private static Map<URI, InstitutionPointsView> indexedInstitutionPoints(
      NviCandidateIndexDocument document) {
    return document.approvals().stream()
        .collect(Collectors.toMap(ApprovalView::institutionId, ApprovalView::points));
  }

  private Map<URI, InstitutionPointsView> candidateInstitutionPoints() {
    return candidate.approvals().keySet().stream()
        .collect(
            Collectors.toMap(
                institutionId -> institutionId,
                institutionId ->
                    InstitutionPointsView.from(
                        candidate.getInstitutionPoints(institutionId).orElseThrow())));
  }

  private static Set<String> indexedCreators(NviCandidateIndexDocument document) {
    return document.publicationDetails().nviContributors().stream()
        .map(contributor -> contributor.id() + "|" + contributor.name())
        .collect(Collectors.toSet());
  }

  private Set<String> candidateCreators() {
    var publicationDetails = candidate.publicationDetails();
    return Stream.concat(
            publicationDetails.verifiedCreators().stream()
                .map(creator -> creator.id() + "|" + creator.name()),
            publicationDetails.unverifiedCreators().stream()
                .map(creator -> null + "|" + creator.name()))
        .collect(Collectors.toSet());
  }
}
