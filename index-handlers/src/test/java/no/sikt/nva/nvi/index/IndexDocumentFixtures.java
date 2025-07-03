package no.sikt.nva.nvi.index;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateDtoInYear;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomNviContributor;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomNviContributorBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPages;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPublicationChannel;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ContributorType;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument.Builder;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.unit.nva.commons.pagination.PaginatedSearchResult;

public final class IndexDocumentFixtures {
  public static final TypeReference<PaginatedSearchResult<NviCandidateIndexDocument>>
      SEARCH_RESULT_TYPE = new TypeReference<>() {};
  private static final int SCALE = 4;
  private static final String DEFAULT_YEAR = String.valueOf(CURRENT_YEAR);

  private IndexDocumentFixtures() {
    // Utility class
  }

  public static List<NviCandidateIndexDocument> createRandomIndexDocuments(
      URI userTopLevelOrganization, int year, int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> createRandomIndexDocument(userTopLevelOrganization, year))
        .toList();
  }

  public static NviCandidateIndexDocument createRandomIndexDocument(
      URI userTopLevelOrganization, int year) {
    return createRandomIndexDocument(userTopLevelOrganization, String.valueOf(year));
  }

  public static NviCandidateIndexDocument createRandomIndexDocument(
      URI userTopLevelOrganization, String year) {
    return createRandomIndexDocumentBuilder(userTopLevelOrganization, year).build();
  }

  public static Builder createRandomIndexDocumentBuilder(URI userTopLevelOrganization, int year) {
    return createRandomIndexDocumentBuilder(userTopLevelOrganization, String.valueOf(year));
  }

  public static Builder createRandomIndexDocumentBuilder(
      URI userTopLevelOrganization, String year) {
    var contributor = randomNviContributorBuilder(userTopLevelOrganization).build();
    var details =
        randomPublicationDetailsBuilder()
            .withPublicationDate(randomPublicationDateDtoInYear(year))
            .withContributors(List.of(contributor))
            .build();
    return randomIndexDocumentBuilder(details);
  }

  public static NviCandidateIndexDocument.Builder randomIndexDocumentBuilder() {
    return randomIndexDocumentBuilder(randomPublicationDetailsBuilder().build());
  }

  public static NviCandidateIndexDocument.Builder randomIndexDocumentBuilder(
      PublicationDetails publicationDetails) {
    var approvals = randomApprovalList();
    var publicationYear = publicationDetails.publicationDate().year();
    var reportingPeriod = new ReportingPeriod(publicationYear);
    var candidateId = randomUUID();
    return NviCandidateIndexDocument.builder()
        .withId(randomUriWithSuffix(candidateId.toString()))
        .withIdentifier(candidateId)
        .withIsApplicable(true)
        .withPublicationDetails(publicationDetails)
        .withApprovals(approvals)
        .withNumberOfApprovals(approvals.size())
        .withPoints(randomBigDecimal())
        .withReportingPeriod(reportingPeriod)
        .withCreatedDate(Instant.now())
        .withModifiedDate(Instant.now());
  }

  public static PublicationDetails.Builder randomPublicationDetailsBuilder(
      Collection<URI> topLevelOrganizations) {
    var contributors =
        topLevelOrganizations.stream()
            .map(id -> randomNviContributorBuilder(id).build())
            .map(ContributorType.class::cast)
            .toList();
    return randomPublicationDetailsBuilder().withContributors(contributors);
  }

  public static PublicationDetails.Builder randomPublicationDetailsBuilder() {
    var publicationId = randomUriWithSuffix(randomUUID().toString());
    return PublicationDetails.builder()
        .withId(publicationId.toString())
        .withTitle(randomString())
        .withAbstract(randomString())
        .withPublicationDate(randomPublicationDateDtoInYear(DEFAULT_YEAR))
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages())
        .withContributors(List.of(randomNviContributor(randomOrganizationId())));
  }

  private static List<Approval> randomApprovalList() {
    return IntStream.range(0, 5).boxed().map(i -> randomApproval()).toList();
  }

  private static Approval randomApproval() {
    return randomApproval(randomString(), randomOrganizationId());
  }

  public static Approval randomApproval(String assignee, URI topLevelOrganization) {
    var creatorAffiliation = randomOrganizationId();
    var creatorPoint =
        new CreatorAffiliationPoints(randomUri(), creatorAffiliation, randomBigDecimal(SCALE));
    var institutionPoints =
        new InstitutionPoints(topLevelOrganization, randomBigDecimal(SCALE), List.of(creatorPoint));
    return new Approval(
        topLevelOrganization,
        Map.of(),
        randomStatus(),
        institutionPoints,
        Set.of(topLevelOrganization, creatorAffiliation),
        assignee,
        randomGlobalApprovalStatus());
  }

  public static ApprovalStatus randomStatus() {
    var values = Arrays.stream(ApprovalStatus.values()).toList();
    var size = values.size();
    var random = new Random();
    return values.get(random.nextInt(size));
  }

  public static GlobalApprovalStatus randomGlobalApprovalStatus() {
    return randomElement(GlobalApprovalStatus.values());
  }
}
