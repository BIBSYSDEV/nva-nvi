package cucumber.steps;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationWithNestedPartOf;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationNode;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
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

  private Organization institution;
  private Organization secondInstitution;
  private Organization sectionA1;
  private Organization sectionA2;
  private ContributorDto creator;
  private ContributorDto coAuthor;
  private SampleExpandedPublicationFactory publicationFactory;
  private Candidate candidate;
  private NviCandidateIndexDocument initialDocument;
  private NviCandidateIndexDocument updatedDocument;

  public IndexingSteps(TestScenario scenario) {
    this.scenario = scenario;
    this.evaluationContext = new EvaluationContext(scenario);
    this.indexingContext = new IndexingContext(scenario);
  }

  @Given("an institution with departments A and B, and sections A1 and A2 under department A")
  public void anInstitutionWithDepartmentsAndSections() {
    var institutionId = randomOrganizationId();
    var departmentAId = randomOrganizationId();
    var departmentBId = randomOrganizationId();
    var sectionA1Id = randomOrganizationId();
    var sectionA2Id = randomOrganizationId();

    sectionA1 = organizationNode(sectionA1Id, departmentAId);
    sectionA2 = organizationNode(sectionA2Id, departmentAId);
    var departmentA = organizationNode(departmentAId, institutionId, sectionA1, sectionA2);
    var departmentB = organizationNode(departmentBId, institutionId);
    institution = organizationNode(institutionId, null, departmentA, departmentB);

    indexingContext.registerOrganization(
        createOrganizationWithNestedPartOf(sectionA1Id, departmentAId, institutionId));
    indexingContext.registerOrganization(
        createOrganizationWithNestedPartOf(sectionA2Id, departmentAId, institutionId));
  }

  @Given("a second institution")
  public void aSecondInstitution() {
    var secondInstitutionId = randomOrganizationId();
    secondInstitution = organizationNode(secondInstitutionId, null);
    indexingContext.registerOrganization(createOrganizationWithNestedPartOf(secondInstitutionId));
  }

  @Given(
      "a Publication co-authored by a creator in section A1 and a creator in the second institution")
  public void aPublicationCoAuthoredBetweenTheTwoInstitutions() {
    creator = verifiedCreatorFrom(sectionA1);
    coAuthor = verifiedCreatorFrom(secondInstitution);
    publicationFactory =
        new SampleExpandedPublicationFactory(
            List.of(
                createCustomer(institution.id(), true),
                createCustomer(secondInstitution.id(), true)));
    publicationFactory
        .withTopLevelOrganizations(List.of(institution, secondInstitution))
        .withContributor(creator)
        .withContributor(coAuthor);
  }

  @Given("a reported Candidate for the Publication")
  public void aReportedCandidateForThePublication() {
    evaluationContext.mockGetAllCustomersResponse(publicationFactory.getCustomerOrganizations());
    setupOpenPeriod(scenario, THIS_YEAR);
    evaluationContext.evaluatePublicationAndPersistResult(
        publicationFactory.getExpandedPublication());

    candidate = scenario.getCandidateByPublicationId(publicationFactory.getPublicationId());
    for (var approvalInstitutionId : candidate.approvals().keySet()) {
      scenario.updateApprovalStatus(
          candidate.identifier(), ApprovalStatus.APPROVED, approvalInstitutionId);
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

  @When("the creator in section A1 is moved to section A2 in the Publication")
  public void theCreatorInSectionA1IsMovedToSectionA2() {
    indexingContext.overwriteSource(candidate, publicationWithCreatorMovedTo(sectionA2));
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
    assertThat(updatedDocument.internationalCollaborationFactor()).isEqualTo(candidate.getCollaborationFactor());
    assertThat(updatedDocument.creatorShareCount()).isEqualTo(candidate.getCreatorShareCount());
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

  private SampleExpandedPublication publicationWithCreatorMovedTo(Organization affiliation) {
    var movedCreator = expandedContributor(creator, affiliation);
    var unchangedCoAuthor = expandedContributor(coAuthor, secondInstitution);
    return publicationFactory
        .getExpandedPublicationBuilder()
        .withContributors(List.of(movedCreator, unchangedCoAuthor))
        .build();
  }

  private static SampleExpandedContributor expandedContributor(
      ContributorDto contributor, Organization affiliation) {
    return SampleExpandedContributor.builder()
        .withId(contributor.id())
        .withNames(List.of(contributor.name()))
        .withRole("Creator")
        .withVerificationStatus("Verified")
        .withOrcId(randomUri())
        .withAffiliations(
            List.of(SampleExpandedPublicationFactory.mapOrganizationToAffiliation(affiliation)))
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
                .flatMap(verifiedCreator -> verifiedCreator.affiliations().stream()),
            publicationDetails.unverifiedCreators().stream()
                .flatMap(unverifiedCreator -> unverifiedCreator.affiliations().stream()))
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
                approvalInstitutionId -> approvalInstitutionId,
                approvalInstitutionId ->
                    InstitutionPointsView.from(
                        candidate.getInstitutionPoints(approvalInstitutionId).orElseThrow())));
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
                .map(verifiedCreator -> verifiedCreator.id() + "|" + verifiedCreator.name()),
            publicationDetails.unverifiedCreators().stream()
                .map(unverifiedCreator -> null + "|" + unverifiedCreator.name()))
        .collect(Collectors.toSet());
  }
}
