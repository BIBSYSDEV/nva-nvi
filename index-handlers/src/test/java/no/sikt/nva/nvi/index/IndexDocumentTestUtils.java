package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getCandidateContextUri;
import static no.sikt.nva.nvi.common.model.EnumFixtures.randomValidScientificValue;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.sikt.nva.nvi.test.TestConstants.CREATOR;
import static no.sikt.nva.nvi.test.TestConstants.EN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.NB_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomIssn;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.Contributor;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView;
import no.sikt.nva.nvi.index.model.document.InstitutionPointsView.CreatorAffiliationPointsView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument.Builder;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.NviOrganization;
import no.sikt.nva.nvi.index.model.document.Organization;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class IndexDocumentTestUtils {

  public static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
  public static final String GZIP_ENDING = ".gz";
  private static final String DELIMITER = "\\.";

  private IndexDocumentTestUtils() {}

  public static UnixPath createPath(Candidate candidate) {
    return UnixPath.of(NVI_CANDIDATES_FOLDER)
        .addChild(candidate.identifier().toString() + GZIP_ENDING);
  }

  public static URI randomCristinOrgUri() {
    return cristinOrgUriWithTopLevel(String.valueOf(randomIntBetween(100_000, 200_000)));
  }

  public static URI cristinOrgUriWithTopLevel(String topLevelIdentifier) {
    var cristinIdentifier =
        String.join(
            ".",
            topLevelIdentifier,
            String.valueOf(randomIntBetween(0, 99)),
            String.valueOf(randomIntBetween(0, 99)),
            String.valueOf(randomIntBetween(0, 99)));
    return UriWrapper.fromUri(randomUri()).addChild(cristinIdentifier).getUri();
  }

  public static NviCandidateIndexDocument randomIndexDocumentWith(int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId).build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutPages(int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId).withPages(null).build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutLanguage(
      int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withLanguage(null)
            .build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithLanguage(
      int currentYear, URI topLevelCristinOrg, String languageUri) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(topLevelCristinOrg)
            .withLanguage(languageUri)
            .build();
    var approvals = createApprovals(topLevelCristinOrg, publicationDetails.nviContributors());
    return getBuilder(currentYear, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutIssn(int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withPublicationChannel(randomPublicationChannelBuilder().withPrintIssn(null).build())
            .build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentWithoutOptionalPublicationChannelData(
      int year, URI institutionId) {
    // This is not a valid state for candidates created in nva-nvi, but it may occur for candidates
    // imported via
    // Cristin.
    var publicationChannel =
        PublicationChannel.builder().withScientificValue(randomValidScientificValue()).build();
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withPublicationChannel(publicationChannel)
            .build();
    var approvals = createApprovals(institutionId, publicationDetails.nviContributors());
    return getBuilder(year, approvals, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentMissingVerifiedCreators(
      int year, URI institutionId) {
    var unverifiedCreators =
        List.of(randomNviContributorBuilder(institutionId).withId(null).build());
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId)
            .withNviContributors(unverifiedCreators)
            .withContributors(asContributors(unverifiedCreators))
            .build();
    var approvalsWithoutCreatorAffiliationPoints = createApprovals(institutionId, emptyList());
    return getBuilder(year, approvalsWithoutCreatorAffiliationPoints, publicationDetails).build();
  }

  public static NviCandidateIndexDocument indexDocumentMissingApprovals(
      int year, URI institutionId) {
    var publicationDetails =
        publicationDetailsWithNviContributorsAffiliatedWith(institutionId).build();
    var noApprovals = new ArrayList<ApprovalView>();
    return getBuilder(year, noApprovals, publicationDetails).build();
  }

  public static PublicationChannel randomPublicationChannel() {
    return randomPublicationChannelBuilder().build();
  }

  public static PublicationChannel.Builder randomPublicationChannelBuilder() {
    return PublicationChannel.builder()
        .withId(randomUri())
        .withType(randomString())
        .withScientificValue(randomValidScientificValue())
        .withName(randomString())
        .withPrintIssn(randomIssn());
  }

  public static Pages randomPages() {
    return Pages.builder()
        .withBegin(randomString())
        .withEnd(randomString())
        .withNumberOfPages(randomString())
        .build();
  }

  public static NviContributor.Builder randomNviContributorBuilder(URI institutionId) {
    return NviContributor.builder()
        .withId(randomUri())
        .withName(randomString())
        .withOrcid(randomString())
        .withRole(CREATOR)
        .withAffiliations(
            List.of(
                randomSubUnitNviAffiliation(institutionId),
                nviOrganization(institutionId),
                nviOrganization(randomUri()),
                randomNonNviAffiliation()));
  }

  public static NviContributor randomNviContributor(URI institutionId) {
    return randomNviContributorBuilder(institutionId).build();
  }

  public static List<Contributor> asContributors(List<NviContributor> nviContributors) {
    return nviContributors.stream().map(IndexDocumentTestUtils::asContributor).toList();
  }

  private static Contributor asContributor(NviContributor nviContributor) {
    return Contributor.builder()
        .withId(nviContributor.id())
        .withName(nviContributor.name())
        .withOrcid(nviContributor.orcid())
        .withRole(nviContributor.role())
        .withAffiliations(nviContributor.affiliations())
        .build();
  }

  private static Builder getBuilder(
      int year, List<ApprovalView> approvals, PublicationDetails publicationDetails) {
    return NviCandidateIndexDocument.builder()
        .withContext(getCandidateContextUri())
        .withId(randomUri())
        .withIsApplicable(true)
        .withIdentifier(UUID.randomUUID())
        .withApprovals(approvals)
        .withPoints(randomBigDecimal())
        .withPublicationDetails(publicationDetails)
        .withNumberOfApprovals(approvals.size())
        .withCreatorShareCount(randomIntBetween(1, 10))
        .withReported(true)
        .withGlobalApprovalStatus(randomElement(GlobalApprovalStatus.values()))
        .withPublicationTypeChannelLevelPoints(randomBigDecimal())
        .withInternationalCollaborationFactor(randomBigDecimal())
        .withCreatedDate(Instant.now())
        .withModifiedDate(Instant.now())
        .withReportingPeriod(new ReportingPeriod(String.valueOf(year)));
  }

  private static PublicationDetails.Builder publicationDetailsWithNviContributorsAffiliatedWith(
      URI institutionId) {
    var nviContributors =
        List.of(randomNviContributor(institutionId), randomNviContributor(institutionId));
    return PublicationDetails.builder()
        .withType(randomString())
        .withId(randomUri().toString())
        .withTitle(randomString())
        .withPublicationDate(getRandomDateInCurrentYearAsDto())
        .withNviContributors(nviContributors)
        .withContributors(asContributors(nviContributors))
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages());
  }

  private static NviOrganization nviOrganization(URI id) {
    return NviOrganization.builder().withId(id).withPartOf(emptyList()).build();
  }

  private static Organization randomNonNviAffiliation() {
    return Organization.builder().withId(randomUri()).withPartOf(List.of(randomUri())).build();
  }

  private static NviOrganization randomSubUnitNviAffiliation(URI institutionId) {
    var topLevelIdentifier =
        UriWrapper.fromUri(institutionId).getLastPathElement().split(DELIMITER)[0];
    var id = cristinOrgUriWithTopLevel(topLevelIdentifier);
    return NviOrganization.builder().withId(id).withPartOf(List.of(institutionId)).build();
  }

  private static List<ApprovalView> createApprovals(URI uri, List<NviContributor> contributors) {
    return List.of(createApproval(uri, contributors, randomElement(GlobalApprovalStatus.values())));
  }

  private static ApprovalView createApproval(
      URI institutionId,
      List<NviContributor> contributors,
      GlobalApprovalStatus globalApprovalStatus) {
    var involvedOrganizations =
        new HashSet<>(filterContributorsPartOf(institutionId, contributors));
    var institutionPoints = generateInstitutionPoints(contributors, institutionId);
    return getApprovalBuilder(
            institutionId, globalApprovalStatus, institutionPoints, involvedOrganizations)
        .build();
  }

  private static ApprovalView.Builder getApprovalBuilder(
      URI institutionId,
      GlobalApprovalStatus globalApprovalStatus,
      InstitutionPointsView institutionPoints,
      Set<URI> involvedOrganizations) {
    return ApprovalView.builder()
        .withInstitutionId(institutionId)
        .withApprovalStatus(ApprovalStatus.NEW)
        .withAssignee(randomString())
        .withPoints(institutionPoints)
        .withInvolvedOrganizations(involvedOrganizations)
        .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL))
        .withGlobalApprovalStatus(globalApprovalStatus);
  }

  private static List<URI> filterContributorsPartOf(
      URI institutionId, List<NviContributor> contributors) {
    return contributors.stream()
        .flatMap(contributor -> contributor.getOrganizationsPartOf(institutionId).stream())
        .toList();
  }

  private static InstitutionPointsView generateInstitutionPoints(
      List<NviContributor> contributors, URI institutionId) {
    var creatorAffiliationPoints =
        contributors.stream()
            .flatMap(IndexDocumentTestUtils::generateListOfCreatorAffiliationPoints)
            .toList();
    return getInstitutionPointsBuilder(institutionId, creatorAffiliationPoints).build();
  }

  private static InstitutionPointsView.Builder getInstitutionPointsBuilder(
      URI institutionId, List<CreatorAffiliationPointsView> creatorAffiliationPoints) {
    return InstitutionPointsView.builder()
        .withInstitutionId(institutionId)
        .withInstitutionPoints(randomBigDecimal())
        .withCreatorAffiliationPoints(creatorAffiliationPoints);
  }

  private static Stream<CreatorAffiliationPointsView> generateListOfCreatorAffiliationPoints(
      NviContributor contributor) {
    return contributor.affiliations().stream()
        .filter(NviOrganization.class::isInstance)
        .map(NviOrganization.class::cast)
        .map(affiliation -> generateCreatorAffiliationPoints(contributor, affiliation));
  }

  private static CreatorAffiliationPointsView generateCreatorAffiliationPoints(
      NviContributor contributor, NviOrganization affiliation) {
    return CreatorAffiliationPointsView.builder()
        .withNviCreator(URI.create(contributor.id()))
        .withAffiliationId(affiliation.id())
        .withPoints(randomBigDecimal())
        .build();
  }
}
