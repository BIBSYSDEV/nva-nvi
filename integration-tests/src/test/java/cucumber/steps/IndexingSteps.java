package cucumber.steps;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.cristin.CristinOrganizationFixtures.organizationWithNestedPartOf;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createCustomer;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.verifiedCreatorFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.organizationNode;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.JOURNAL_TYPE;
import static no.sikt.nva.nvi.test.TestConstants.LEVEL_ONE;
import static no.sikt.nva.nvi.test.TestConstants.LEVEL_TWO;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.ContributorDto;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
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
  private ContributorDto addedCreator;
  private SampleExpandedPublicationFactory publicationFactory;
  private Candidate candidate;
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
        organizationWithNestedPartOf(sectionA1Id, departmentAId, institutionId));
    indexingContext.registerOrganization(
        organizationWithNestedPartOf(sectionA2Id, departmentAId, institutionId));
  }

  @Given("a second institution")
  public void aSecondInstitution() {
    var secondInstitutionId = randomOrganizationId();
    secondInstitution = organizationNode(secondInstitutionId, null);
    indexingContext.registerOrganization(organizationWithNestedPartOf(secondInstitutionId));
  }

  @Given(
      "a Publication co-authored by a creator in section A1 and a creator in the second"
          + " institution")
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

  @When("the Candidate is indexed")
  public void theCandidateIsIndexed() {
    indexingContext.index(candidate);
    updatedDocument = indexingContext.readIndexDocument(candidate).indexDocument();
  }

  @Given("the creator in section A1 is moved to section A2 in the Publication")
  public void theCreatorInSectionA1IsMovedToSectionA2() {
    indexingContext.overwriteSource(candidate, publicationWithCreatorMovedTo(sectionA2));
  }

  @Given("a creator is added to the Publication")
  public void aCreatorIsAddedToThePublication() {
    addedCreator = verifiedCreatorFrom(sectionA2);
    var publication =
        publicationFactory
            .getExpandedPublicationBuilder()
            .withContributors(
                List.of(
                    expandedContributor(creator, sectionA1),
                    expandedContributor(coAuthor, secondInstitution),
                    expandedContributor(addedCreator, sectionA2)))
            .build();
    indexingContext.overwriteSource(candidate, publication);
  }

  @Given("the creator in section A1 is removed from the Publication")
  public void theCreatorInSectionA1IsRemovedFromThePublication() {
    var publication =
        publicationFactory
            .getExpandedPublicationBuilder()
            .withContributors(List.of(expandedContributor(coAuthor, secondInstitution)))
            .build();
    indexingContext.overwriteSource(candidate, publication);
  }

  @Given("the channel level in the Publication is changed from level 1 to level 2")
  public void theChannelLevelInThePublicationIsChangedFromLevel1ToLevel2() {
    publicationFactory.withPublicationChannel(JOURNAL_TYPE, LEVEL_TWO);
    indexingContext.overwriteSource(candidate, publicationFactory.getExpandedPublication());
  }

  @Then("the index document has the same NVI data as the Candidate")
  public void theIndexDocumentHasTheSameNviDataAsTheCandidate() {
    theIndexDocumentHasTheSameNviPointsAsTheCandidate();
    theIndexDocumentHasTheSameChannelLevelAsTheCandidate();
    theIndexDocumentHasTheSameChannelIdAsTheCandidate();
    theIndexDocumentHasTheSameNviAffiliationsAsTheCandidate();
    theIndexDocumentHasTheSameNviCreatorsAsTheCandidate();
    theIndexDocumentHasTheSameApprovalStatusesAsTheCandidate();
    theIndexDocumentHasTheSameReportingStatusAsTheCandidate();
  }

  @Then("the index document has the same NVI affiliations as the Candidate")
  public void theIndexDocumentHasTheSameNviAffiliationsAsTheCandidate() {
    assertThat(indexedNviAffiliations(updatedDocument)).isEqualTo(candidateAffiliations());
  }

  @Then("the index document has the same NVI points as the Candidate")
  public void theIndexDocumentHasTheSameNviPointsAsTheCandidate() {
    assertThat(indexedInstitutionPoints(updatedDocument)).isEqualTo(candidateInstitutionPoints());
    assertThat(updatedDocument.points()).isEqualByComparingTo(candidate.getTotalPoints());
    assertThat(updatedDocument.internationalCollaborationFactor())
        .isEqualTo(candidate.getCollaborationFactor());
    assertThat(updatedDocument.creatorShareCount()).isEqualTo(candidate.getCreatorShareCount());
  }

  @Then("the index document has the same NVI creators as the Candidate")
  public void theIndexDocumentHasTheSameNviCreatorsAsTheCandidate() {
    assertThat(indexedCreators(updatedDocument)).isEqualTo(candidateCreators());
  }

  @Then("the index document has the same reporting status as the Candidate")
  public void theIndexDocumentHasTheSameReportingStatusAsTheCandidate() {
    assertThat(updatedDocument.reported()).isEqualTo(candidate.isReported());
    assertThat(updatedDocument.reportingPeriod())
        .isEqualTo(ReportingPeriod.fromCandidate(candidate));
    var expectedReportedDate =
        Optional.ofNullable(candidate.reportedDate()).map(Instant::toString).orElse(null);
    assertThat(updatedDocument.reportedDate()).isEqualTo(expectedReportedDate);
  }

  @Then("the index document has the same approval statuses as the Candidate")
  public void theIndexDocumentHasTheSameApprovalStatusesAsTheCandidate() {
    assertThat(indexedApprovalStatuses(updatedDocument)).isEqualTo(candidateApprovalStatuses());
    assertThat(updatedDocument.globalApprovalStatus())
        .isEqualTo(candidate.getGlobalApprovalStatus());
  }

  @Then("the added creator is not indexed as an NVI creator")
  public void theAddedCreatorIsNotIndexedAsAnNviCreator() {
    assertThat(indexedNviCreatorIds()).doesNotContain(addedCreator.id().toString());
  }

  @Then("the creator in section A1 is still indexed as an NVI creator")
  public void theCreatorInSectionA1IsStillIndexedAsAnNviCreator() {
    assertThat(indexedNviCreatorIds()).contains(creator.id().toString());
  }

  @Then("the creator is indexed as affiliated with section A1, not section A2")
  public void theCreatorIsIndexedAsAffiliatedWithSectionA1NotSectionA2() {
    var indexedAffiliations = indexedNviAffiliations(updatedDocument).get(creator.id().toString());
    assertThat(indexedAffiliations).contains(sectionA1.id()).doesNotContain(sectionA2.id());
  }

  @Then("the added creator is indexed as a searchable contributor")
  public void theAddedCreatorIsIndexedAsASearchableContributor() {
    assertThat(searchableContributorIds()).contains(addedCreator.id().toString());
  }

  @Then("all contributors are indexed as searchable, including non-NVI ones")
  public void allContributorsAreIndexedAsSearchableIncludingNonNviOnes() {
    assertThat(searchableContributorIds())
        .contains(creator.id().toString(), coAuthor.id().toString(), addedCreator.id().toString());
    assertThat(indexedNviCreatorIds()).doesNotContain(addedCreator.id().toString());
  }

  @Then("the indexed creators have names")
  public void theIndexedCreatorsHaveNames() {
    assertThat(updatedDocument.publicationDetails().nviContributors())
        .isNotEmpty()
        .allSatisfy(contributor -> assertThat(contributor.name()).isNotBlank());
  }

  @Then("the index document has the same channel level as the Candidate")
  public void theIndexDocumentHasTheSameChannelLevelAsTheCandidate() {
    var candidateLevel =
        ScientificValue.parse(candidate.getPublicationChannel().scientificValue().getValue());
    assertThat(updatedDocument.publicationDetails().publicationChannel().scientificValue())
        .isEqualTo(candidateLevel);
  }

  @Then("the index document has the same channel ID as the Candidate")
  public void theIndexDocumentHasTheSameChannelIdAsTheCandidate() {
    assertThat(updatedDocument.publicationDetails().publicationChannel().id())
        .isEqualTo(candidate.getPublicationChannel().id());
  }

  @Then("the indexed channel has a name")
  public void theIndexedChannelHasAName() {
    assertThat(updatedDocument.publicationDetails().publicationChannel().name()).isNotBlank();
  }

  @Then("the indexed channel is level 1, not level 2")
  public void theIndexedChannelIsLevel1NotLevel2() {
    var indexedLevel = updatedDocument.publicationDetails().publicationChannel().scientificValue();
    assertThat(indexedLevel)
        .isEqualTo(ScientificValue.parse(LEVEL_ONE))
        .isNotEqualTo(ScientificValue.parse(LEVEL_TWO));
  }

  private Set<String> indexedNviCreatorIds() {
    return updatedDocument.publicationDetails().nviContributors().stream()
        .map(NviContributor::id)
        .collect(Collectors.toSet());
  }

  private Set<String> searchableContributorIds() {
    return updatedDocument.publicationDetails().contributors().stream()
        .map(ContributorType::id)
        .collect(Collectors.toSet());
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

  /**
   * Grouped per creator so a cross-wired creator/affiliation pairing is caught, not just a change
   * in the flattened union.
   */
  private static Map<String, Set<URI>> indexedNviAffiliations(NviCandidateIndexDocument document) {
    return document.publicationDetails().nviContributors().stream()
        .collect(
            Collectors.toMap(IndexingSteps::creatorIdentity, IndexingSteps::nviAffiliationIds));
  }

  private static Set<URI> nviAffiliationIds(NviContributor contributor) {
    return contributor.nviAffiliations().stream()
        .map(NviOrganization::id)
        .collect(Collectors.toSet());
  }

  private Map<String, Set<URI>> candidateAffiliations() {
    var publicationDetails = candidate.publicationDetails();
    return Stream.concat(
            publicationDetails.verifiedCreators().stream()
                .map(
                    verifiedCreator ->
                        Map.entry(
                            verifiedCreator.id().toString(),
                            Set.copyOf(verifiedCreator.affiliations()))),
            publicationDetails.unverifiedCreators().stream()
                .map(
                    unverifiedCreator ->
                        Map.entry(
                            unverifiedCreator.name(),
                            Set.copyOf(unverifiedCreator.affiliations()))))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

  /**
   * Identity of an NVI creator: verified creators are identified by ID, unverified creators by
   * name. The verified creator name is deliberately excluded, since it is enrichment from the
   * Publication until it is persisted on the Candidate (NP-51414); its freezing is covered by a
   * separate scenario.
   */
  private static Set<String> indexedCreators(NviCandidateIndexDocument document) {
    return document.publicationDetails().nviContributors().stream()
        .map(IndexingSteps::creatorIdentity)
        .collect(Collectors.toSet());
  }

  private static String creatorIdentity(NviContributor contributor) {
    return nonNull(contributor.id()) ? contributor.id() : contributor.name();
  }

  private Set<String> candidateCreators() {
    var publicationDetails = candidate.publicationDetails();
    return Stream.concat(
            publicationDetails.verifiedCreators().stream()
                .map(verifiedCreator -> verifiedCreator.id().toString()),
            publicationDetails.unverifiedCreators().stream().map(UnverifiedNviCreatorDto::name))
        .collect(Collectors.toSet());
  }

  /**
   * Approval status keyed by institution, compared by its value string. This is valid because a
   * reported Candidate's approvals are all decided, so the index never applies the NEW
   * (pending-and-unassigned) transform.
   */
  private static Map<URI, String> indexedApprovalStatuses(NviCandidateIndexDocument document) {
    return document.approvals().stream()
        .collect(
            Collectors.toMap(
                ApprovalView::institutionId, approval -> approval.approvalStatus().getValue()));
  }

  private Map<URI, String> candidateApprovalStatuses() {
    return candidate.approvals().values().stream()
        .collect(
            Collectors.toMap(Approval::institutionId, approval -> approval.status().getValue()));
  }
}
