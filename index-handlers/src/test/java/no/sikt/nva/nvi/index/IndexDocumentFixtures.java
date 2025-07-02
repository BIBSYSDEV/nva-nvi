package no.sikt.nva.nvi.index;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument.Builder;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.core.paths.UriWrapper;

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
    var publicationDate = new PublicationDateDto(year, null, null);
    var contributor = randomNviContributorBuilder(userTopLevelOrganization).build();
    var details =
        randomPublicationDetailsBuilder(year)
            .withPublicationDate(publicationDate)
            .withContributors(List.of(contributor))
            .build();
    return randomIndexDocumentBuilder(details).build();
  }

  public static Builder createRandomIndexDocumentBuilder(
      URI userTopLevelOrganization, String year) {
    var publicationDate = new PublicationDateDto(year, null, null);
    var contributor = randomNviContributorBuilder(userTopLevelOrganization).build();
    var details =
        randomPublicationDetailsBuilder(year)
            .withPublicationDate(publicationDate)
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
    return NviCandidateIndexDocument.builder()
        .withIdentifier(randomUUID())
        .withPublicationDetails(publicationDetails)
        .withApprovals(approvals)
        .withNumberOfApprovals(approvals.size())
        .withPoints(randomBigDecimal())
        .withReportingPeriod(reportingPeriod)
        .withCreatedDate(Instant.now())
        .withModifiedDate(Instant.now());
  }

  public static PublicationDetails.Builder randomPublicationDetailsBuilder(String publicationYear) {
    var publicationDate = new PublicationDateDto(publicationYear, null, null);
    return PublicationDetails.builder()
        .withId(UriWrapper.fromUri(randomUri()).addChild(randomUUID().toString()).toString())
        .withTitle(randomString())
        .withAbstract(randomString())
        .withPublicationDate(publicationDate)
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages())
        .withContributors(List.of(randomNviContributor(randomOrganizationId())));
  }

  public static PublicationDetails.Builder randomPublicationDetailsBuilder() {
    var publicationDate = new PublicationDateDto(DEFAULT_YEAR, null, null);
    var publicationId = randomUriWithSuffix(randomUUID().toString());
    return PublicationDetails.builder()
        .withId(publicationId.toString())
        .withTitle(randomString())
        .withAbstract(randomString())
        .withPublicationDate(publicationDate)
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages())
        .withContributors(List.of(randomNviContributor(randomOrganizationId())));
  }

  private static List<Approval> randomApprovalList() {
    return IntStream.range(0, 5).boxed().map(i -> randomApproval()).toList();
  }

  private static Approval randomApproval() {
    return new Approval(
        randomOrganizationId(),
        Map.of(),
        randomStatus(),
        randomInstitutionPoints(),
        Set.of(),
        null,
        randomGlobalApprovalStatus());
  }

  public static ApprovalStatus randomStatus() {
    var values = Arrays.stream(ApprovalStatus.values()).toList();
    var size = values.size();
    var random = new Random();
    return values.get(random.nextInt(size));
  }

  public static InstitutionPoints randomInstitutionPoints() {
    return new InstitutionPoints(
        randomOrganizationId(), randomBigDecimal(SCALE), randomCreatorAffiliationPoints());
  }

  public static GlobalApprovalStatus randomGlobalApprovalStatus() {
    return randomElement(GlobalApprovalStatus.values());
  }

  private static List<CreatorAffiliationPoints> randomCreatorAffiliationPoints() {
    return List.of(
        new CreatorAffiliationPoints(randomUri(), randomOrganizationId(), randomBigDecimal(SCALE)));
  }
}
