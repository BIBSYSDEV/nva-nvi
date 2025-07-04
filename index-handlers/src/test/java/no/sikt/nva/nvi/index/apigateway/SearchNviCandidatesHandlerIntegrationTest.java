package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocuments;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApprovalBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomPublicationDetailsBuilder;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_ASSIGNEE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_EXCLUDE_UNASSIGNED;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_GLOBAL_STATUS;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_OFFSET;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SIZE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_STATUS;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.AccessRight;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SearchNviCandidatesHandlerIntegrationTest extends SearchNviCandidatesHandlerTestBase {
  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static final String OUR_USER = "Current user";
  private static final String OUR_OTHER_USER = "Curator from our organization";
  private static final String THEIR_USER = "Curator from another organization";
  private static final URI OUR_ORGANIZATION = randomOrganizationId();
  private static final URI THEIR_ORGANIZATION = randomOrganizationId();

  @BeforeAll
  static void beforeAll() {
    CONTAINER.start();
    mockIdentityService(OUR_USER, OUR_ORGANIZATION);
    mockIdentityService(OUR_OTHER_USER, OUR_ORGANIZATION);
    mockIdentityService(THEIR_USER, THEIR_ORGANIZATION);
  }

  @AfterAll
  static void afterAll() {
    CONTAINER.stop();
  }

  @BeforeEach
  void beforeEach() {
    currentUsername = OUR_USER;
    currentOrganization = OUR_ORGANIZATION;
    currentAccessRight = AccessRight.MANAGE_NVI_CANDIDATES;

    CONTAINER.createIndex();
    createHandler(CONTAINER.getOpenSearchClient());
  }

  @AfterEach
  void afterEach() {
    CONTAINER.deleteIndex();
  }

  @Nested
  @DisplayName("Access control")
  class AccessControlTests {

    @Test
    void shouldNotReturnNviCandidatesOutsideViewingScope() {
      var expectedDocuments = createRandomIndexDocuments(currentOrganization, CURRENT_YEAR, 2);
      addDocumentsToIndex(expectedDocuments);
      addDocumentsToIndex(createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR));

      var response = handleRequest(emptyMap());

      assertThatResponseHasExpectedIndexDocuments(response, expectedDocuments);
    }

    @Test
    void shouldReturnAllNviCandidatesWhenSearchingWithNviAdminAccessRight() {
      var expectedDocuments =
          List.of(
              createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR),
              createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR));
      addDocumentsToIndex(expectedDocuments);
      currentAccessRight = AccessRight.MANAGE_NVI;

      var response = handleRequest(emptyMap());

      assertThatResponseHasExpectedIndexDocuments(response, expectedDocuments);
    }
  }

  @Nested
  @DisplayName("Pagination and sorting")
  class PaginationTests {
    private static final int DEFAULT_SIZE = 10;
    private static final int DEFAULT_OFFSET = 0;
    private static final int TOTAL_DOCUMENT_COUNT = 25;
    private List<NviCandidateIndexDocument> expectedDocuments;

    @BeforeEach
    void beforeEach() {
      expectedDocuments =
          createRandomIndexDocuments(currentOrganization, CURRENT_YEAR, TOTAL_DOCUMENT_COUNT);
      addDocumentsToIndex(expectedDocuments);
    }

    @Test
    void shouldHaveDefaultValuesIfNotSet() {
      var response = handleRequest(emptyMap());
      assertThat(response.getHits()).hasSize(10);
      assertThat(response.getId().toString())
          .contains(
              QUERY_PARAM_OFFSET + "=" + DEFAULT_OFFSET, QUERY_PARAM_SIZE + "=" + DEFAULT_SIZE);
    }

    @Test
    void shouldHaveExactValuesIfNotSet() {
      var response = handleRequest(Map.of(QUERY_PARAM_OFFSET, "20", QUERY_PARAM_SIZE, "5"));
      assertThat(response.getHits()).hasSize(5);
      assertThat(response.getId().toString())
          .contains(QUERY_PARAM_OFFSET + "=20", QUERY_PARAM_SIZE + "=5");
    }

    @Test
    void shouldGetLastFiveDocuments() {
      var response = handleRequest(Map.of(QUERY_PARAM_OFFSET, "20"));
      assertThat(response.getHits()).hasSize(5);
    }

    @Test
    void shouldGetAllDocumentsWhenScanningSequentially() {
      var response1 = handleRequest(emptyMap());
      var response2 = handleRequest(Map.of(QUERY_PARAM_OFFSET, "10"));
      var response3 = handleRequest(Map.of(QUERY_PARAM_OFFSET, "20"));
      var actualDocumentIds =
          Stream.of(response1, response2, response3)
              .map(PaginatedSearchResult::getHits)
              .flatMap(List::stream)
              .map(NviCandidateIndexDocument::id)
              .toList();
      var expectedDocumentIds =
          expectedDocuments.stream().map(NviCandidateIndexDocument::id).toList();
      assertThat(actualDocumentIds)
          .hasSize(TOTAL_DOCUMENT_COUNT)
          .containsExactlyInAnyOrderElementsOf(expectedDocumentIds);
    }
  }

  @Nested
  @DisplayName("Filter by year")
  class YearTests {
    private NviCandidateIndexDocument documentFromCurrentYear;
    private NviCandidateIndexDocument documentFromLastYear;

    @BeforeEach
    void beforeEach() {
      documentFromCurrentYear = createRandomIndexDocument(currentOrganization, CURRENT_YEAR);
      documentFromLastYear = createRandomIndexDocument(currentOrganization, CURRENT_YEAR - 1);
      addDocumentsToIndex(documentFromCurrentYear, documentFromLastYear);
    }

    @Test
    void shouldIncludeYearInId() {
      var filterYear = String.valueOf(CURRENT_YEAR - 1);
      var response = handleRequest(Map.of(QUERY_PARAM_YEAR, filterYear));
      assertThat(response.getId().toString()).contains(QUERY_PARAM_YEAR + "=", filterYear);
    }

    @Test
    void shouldDefaultToCurrentYear() {
      var response = handleRequest(emptyMap());
      assertThatResponseHasExpectedIndexDocuments(response, List.of(documentFromCurrentYear));
    }

    @Test
    void shouldFilterByReportingPeriodIfSet() {
      var response = handleRequest(Map.of(QUERY_PARAM_YEAR, String.valueOf(CURRENT_YEAR - 1)));
      assertThatResponseHasExpectedIndexDocuments(response, List.of(documentFromLastYear));
    }

    @Test
    void shouldFilterByReportingPeriodAndNotPublicationYear() {
      var reportedYear = String.valueOf(CURRENT_YEAR - 1);
      var expectedDocument =
          createRandomIndexDocumentBuilder(currentOrganization, String.valueOf(CURRENT_YEAR))
              .withReportingPeriod(new ReportingPeriod(reportedYear))
              .build();
      addDocumentsToIndex(expectedDocument);

      var response = handleRequest(Map.of(QUERY_PARAM_YEAR, reportedYear));
      assertThatResponseHasExpectedIndexDocuments(
          response, List.of(expectedDocument, documentFromLastYear));
    }
  }

  @Nested
  @DisplayName("Filter by assignee and excludeUnassigned")
  class AssigneeTests {
    private static final String ASSIGNED_TO_CURRENT = "assignedToCurrentUser";
    private static final String ASSIGNED_TO_OTHER = "assignedToOtherUser";
    private static final String UNASSIGNED_BY_US = "unassignedByUs";
    private static final String UNASSIGNED_BY_THEM = "unassignedByThem";
    private static final String UNASSIGNED_BY_ALL = "unassignedByAll";
    private static final String UNRELATED_ASSIGNED = "assignedAndUnrelated";
    private static final String UNRELATED_UNASSIGNED = "unassignedAndUnrelated";

    private static final Map<String, NviCandidateIndexDocument> docs =
        createDocumentsForAssigneeTests();

    @BeforeEach
    void beforeEach() {
      addDocumentsToIndex(docs.values());
    }

    @ParameterizedTest
    @MethodSource("assigneeTestCaseProvider")
    void shouldFilterByAssigneeAndExcludeUnassignedFilter(
        Map<String, String> queryParameters, Collection<String> expectedDocumentKeys) {
      var response = handleRequest(queryParameters);

      assertThat(response.getHits())
          .hasSameSizeAs(expectedDocumentKeys)
          .allSatisfy(
              doc -> {
                var actualTitle = doc.publicationDetails().title();
                assertThat(doc.id()).isEqualTo(docs.get(actualTitle).id());
                assertThat(actualTitle).isIn(expectedDocumentKeys);
              });
    }

    /**
     * This creates a map of example documents that should cover all realistic scenarios for
     * assignee status. Each document is given a title matching the map key.
     */
    private static Map<String, NviCandidateIndexDocument> createDocumentsForAssigneeTests() {
      var assignedToCurrentUser = randomApproval(OUR_USER, OUR_ORGANIZATION);
      var assignedToOtherUser = randomApproval(OUR_OTHER_USER, OUR_ORGANIZATION);
      var assignedToTheirUser = randomApproval(THEIR_USER, THEIR_ORGANIZATION);
      var noAssigneeFromUs = randomApproval(null, OUR_ORGANIZATION);
      var noAssigneeFromThem = randomApproval(null, THEIR_ORGANIZATION);

      return Map.of(
          ASSIGNED_TO_CURRENT,
          documentWithApprovals(ASSIGNED_TO_CURRENT, assignedToCurrentUser, noAssigneeFromThem),
          ASSIGNED_TO_OTHER,
          documentWithApprovals(ASSIGNED_TO_OTHER, assignedToOtherUser, assignedToTheirUser),
          UNASSIGNED_BY_US,
          documentWithApprovals(UNASSIGNED_BY_US, noAssigneeFromUs, assignedToTheirUser),
          UNASSIGNED_BY_THEM,
          documentWithApprovals(UNASSIGNED_BY_THEM, assignedToOtherUser, noAssigneeFromThem),
          UNASSIGNED_BY_ALL,
          documentWithApprovals(UNASSIGNED_BY_ALL, noAssigneeFromUs, noAssigneeFromThem),
          UNRELATED_ASSIGNED,
          documentWithApprovals(UNRELATED_ASSIGNED, assignedToTheirUser),
          UNRELATED_UNASSIGNED,
          documentWithApprovals(UNRELATED_UNASSIGNED, noAssigneeFromThem));
    }

    /**
     * This sets up test cases for all combinations of the query parameters `assignee` and
     * `excludeUnassigned`. It creates two values for each case: - The query parameters to use in
     * the request - Names of the documents we expect to get in the response
     */
    private static Stream<Arguments> assigneeTestCaseProvider() {
      return Stream.of(
          argumentSet(
              "Query without filters",
              emptyMap(),
              List.of(
                  ASSIGNED_TO_CURRENT,
                  ASSIGNED_TO_OTHER,
                  UNASSIGNED_BY_US,
                  UNASSIGNED_BY_THEM,
                  UNASSIGNED_BY_ALL)),
          argumentSet(
              "assignee=someone",
              Map.of(QUERY_PARAM_ASSIGNEE, OUR_USER),
              List.of(ASSIGNED_TO_CURRENT, UNASSIGNED_BY_US, UNASSIGNED_BY_ALL)),
          argumentSet(
              "excludeUnassigned=false",
              Map.of(QUERY_PARAM_EXCLUDE_UNASSIGNED, "false"),
              List.of(
                  ASSIGNED_TO_CURRENT,
                  ASSIGNED_TO_OTHER,
                  UNASSIGNED_BY_US,
                  UNASSIGNED_BY_THEM,
                  UNASSIGNED_BY_ALL)),
          argumentSet(
              "excludeUnassigned=true",
              Map.of(QUERY_PARAM_EXCLUDE_UNASSIGNED, "true"),
              List.of(ASSIGNED_TO_CURRENT, ASSIGNED_TO_OTHER, UNASSIGNED_BY_THEM)),
          argumentSet(
              "assignee=someone & excludeUnassigned=false",
              Map.of(QUERY_PARAM_ASSIGNEE, OUR_OTHER_USER, QUERY_PARAM_EXCLUDE_UNASSIGNED, "false"),
              List.of(ASSIGNED_TO_OTHER, UNASSIGNED_BY_US, UNASSIGNED_BY_THEM, UNASSIGNED_BY_ALL)),
          argumentSet(
              "assignee=someone & excludeUnassigned=true",
              Map.of(QUERY_PARAM_ASSIGNEE, OUR_OTHER_USER, QUERY_PARAM_EXCLUDE_UNASSIGNED, "true"),
              List.of(ASSIGNED_TO_OTHER, UNASSIGNED_BY_THEM)));
    }
  }

  @Nested
  @DisplayName("Filter by approval status")
  class StatusTests {
    private static final Collection<NviCandidateIndexDocument> docsForStatusCombinations =
        createDocsForAllApprovalStatusCombinations();

    @ParameterizedTest
    @MethodSource("queryParameterToExpectedApprovalStatus")
    void shouldFilterByApprovalStatus(
        Map<String, String> queryParameters, Collection<ApprovalStatus> expectedStatuses) {
      addDocumentsToIndex(docsForStatusCombinations);

      var response = handleRequest(queryParameters);

      assertThat(response.getHits())
          .extracting(doc -> doc.getApprovalStatusForInstitution(OUR_ORGANIZATION))
          .hasSameElementsAs(expectedStatuses);
    }

    @ParameterizedTest
    @MethodSource("queryParameterToExpectedGlobalStatus")
    void shouldFilterByMultipleGlobalApprovalStatus(
        Map<String, String> queryParameters, Collection<GlobalApprovalStatus> expectedStatuses) {
      addDocumentsToIndex(docsForStatusCombinations);

      var response = handleRequest(queryParameters);

      assertThat(response.getHits())
          .extracting(NviCandidateIndexDocument::globalApprovalStatus)
          .hasSameElementsAs(expectedStatuses);
    }

    @ParameterizedTest
    @EnumSource(GlobalApprovalStatus.class)
    void shouldFilterBySingleGlobalApprovalStatus(GlobalApprovalStatus globalStatus) {
      addDocumentsToIndex(docsForStatusCombinations);

      var response = handleRequest(Map.of(QUERY_PARAM_GLOBAL_STATUS, globalStatus.toString()));

      assertThat(response.getHits())
          .extracting(NviCandidateIndexDocument::globalApprovalStatus)
          .containsOnly(globalStatus);
    }

    private static List<Approval> getAllPossibleApprovals(URI topLevelOrganization) {
      var baseApproval = randomApprovalBuilder(topLevelOrganization);
      return Stream.of(ApprovalStatus.values())
          .map(status -> baseApproval.withApprovalStatus(status).build())
          .toList();
    }

    private static List<NviCandidateIndexDocument> createDocsForAllApprovalStatusCombinations() {
      var ourApprovals = getAllPossibleApprovals(OUR_ORGANIZATION);
      var theirApprovals = getAllPossibleApprovals(THEIR_ORGANIZATION);

      var documents = new ArrayList<NviCandidateIndexDocument>();
      for (var theirApproval : theirApprovals) {
        documents.add(documentWithApprovals("Unrelated document", theirApproval));
        for (var ourApproval : ourApprovals) {
          var title =
              String.format(
                  "Collaboration (%s / %s)",
                  ourApproval.approvalStatus(), theirApproval.approvalStatus());
          documents.add(documentWithApprovals(title, ourApproval, theirApproval));
        }
      }
      return documents;
    }

    private static List<NviCandidateIndexDocument> createDocsForAllGlobalStatusCombinations() {
      var ourApprovals = getAllPossibleApprovals(OUR_ORGANIZATION);

      var documents = new ArrayList<NviCandidateIndexDocument>();
      for (var globalStatus : GlobalApprovalStatus.values()) {
        for (var ourApproval : ourApprovals) {
          var title = String.format("Status (%s / %s)", ourApproval.approvalStatus(), globalStatus);
          documents.add(documentWithApprovals(title, globalStatus, ourApproval));
        }
      }
      return documents;
    }

    private static Stream<Arguments> queryParameterToExpectedApprovalStatus() {
      var allStatuses = Arrays.stream(ApprovalStatus.values()).toList();
      return Stream.of(
          argumentSet("Query without filters", Map.of(QUERY_PARAM_SIZE, "50"), allStatuses),
          argumentSet(
              "status=pending,approved,rejected",
              Map.of(QUERY_PARAM_STATUS, "pending,approved,rejected", QUERY_PARAM_SIZE, "50"),
              allStatuses),
          argumentSet(
              "status=pending", Map.of(QUERY_PARAM_STATUS, "pending"), List.of(NEW, PENDING)),
          argumentSet("status=approved", Map.of(QUERY_PARAM_STATUS, "approved"), List.of(APPROVED)),
          argumentSet("status=rejected", Map.of(QUERY_PARAM_STATUS, "rejected"), List.of(REJECTED)),
          argumentSet(
              "status=pending,approved",
              Map.of(QUERY_PARAM_STATUS, "pending,approved"),
              List.of(NEW, PENDING, APPROVED)),
          argumentSet(
              "status=pending,rejected",
              Map.of(QUERY_PARAM_STATUS, "pending,rejected"),
              List.of(NEW, PENDING, REJECTED)),
          argumentSet(
              "status=approved,rejected",
              Map.of(QUERY_PARAM_STATUS, "approved,rejected"),
              List.of(APPROVED, REJECTED)));
    }

    private static Stream<Arguments> queryParameterToExpectedGlobalStatus() {
      var allStatuses = Arrays.stream(GlobalApprovalStatus.values()).toList();
      return Stream.of(
          argumentSet("Query without filters", Map.of(QUERY_PARAM_SIZE, "50"), allStatuses),
          argumentSet(
              "globalStatus=pending,approved,rejected,dispute",
              Map.of(
                  QUERY_PARAM_GLOBAL_STATUS,
                  "pending,approved,rejected,dispute",
                  QUERY_PARAM_SIZE,
                  "50"),
              allStatuses),
          argumentSet(
              "globalStatus=pending,approved",
              Map.of(QUERY_PARAM_GLOBAL_STATUS, "pending,approved"),
              List.of(GlobalApprovalStatus.PENDING, GlobalApprovalStatus.APPROVED)),
          argumentSet(
              "globalStatus=pending,rejected",
              Map.of(QUERY_PARAM_GLOBAL_STATUS, "pending,rejected"),
              List.of(GlobalApprovalStatus.PENDING, GlobalApprovalStatus.REJECTED)),
          argumentSet(
              "globalStatus=approved",
              Map.of(QUERY_PARAM_GLOBAL_STATUS, "approved, rejected"),
              List.of(GlobalApprovalStatus.APPROVED, GlobalApprovalStatus.REJECTED)));
    }
  }

  private static void addDocumentsToIndex(NviCandidateIndexDocument... documents) {
    CONTAINER.addDocumentsToIndex(documents);
  }

  private static void addDocumentsToIndex(Collection<NviCandidateIndexDocument> documents) {
    CONTAINER.addDocumentsToIndex(documents);
  }

  private static void assertThatResponseHasExpectedIndexDocuments(
      PaginatedSearchResult<NviCandidateIndexDocument> response,
      Collection<NviCandidateIndexDocument> expectedDocuments) {
    assertThatResultsAreEqual(response.getHits(), expectedDocuments);
  }

  private static void assertThatResultsAreEqual(
      Collection<NviCandidateIndexDocument> actualDocuments,
      Collection<NviCandidateIndexDocument> expectedDocuments) {
    assertThat(actualDocuments)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("publicationDetails.contributors")
        .isEqualTo(expectedDocuments);
  }

  private static NviCandidateIndexDocument documentWithApprovals(
      String title, Approval... approvals) {
    var approvalStatuses =
        Stream.of(approvals).map(Approval::approvalStatus).collect(Collectors.toSet());
    var globalStatus = expectedGlobalApprovalStatus(approvalStatuses);
    return documentWithApprovals(title, globalStatus, approvals);
  }

  private static GlobalApprovalStatus expectedGlobalApprovalStatus(
      Set<ApprovalStatus> approvalStatuses) {
    if (approvalStatuses.equals(Set.of(APPROVED))) {
      return GlobalApprovalStatus.APPROVED;
    }
    if (approvalStatuses.equals(Set.of(REJECTED))) {
      return GlobalApprovalStatus.REJECTED;
    }
    if (approvalStatuses.containsAll(Set.of(APPROVED, REJECTED))) {
      return GlobalApprovalStatus.DISPUTE;
    }
    return GlobalApprovalStatus.PENDING;
  }

  private static NviCandidateIndexDocument documentWithApprovals(
      String title, GlobalApprovalStatus globalStatus, Approval... approvals) {
    var allApprovals = List.of(approvals);
    var topLevelOrganizations = allApprovals.stream().map(Approval::institutionId).toList();
    var details = randomPublicationDetailsBuilder(topLevelOrganizations).withTitle(title).build();
    return randomIndexDocumentBuilder(details)
        .withGlobalApprovalStatus(globalStatus)
        .withApprovals(allApprovals)
        .withNumberOfApprovals(allApprovals.size())
        .build();
  }
}
