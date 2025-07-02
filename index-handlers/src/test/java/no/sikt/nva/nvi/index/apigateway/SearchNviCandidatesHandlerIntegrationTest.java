package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocuments;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_OFFSET;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SIZE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
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

class SearchNviCandidatesHandlerIntegrationTest extends SearchNviCandidatesHandlerTestBase {
  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();

  @BeforeAll
  static void beforeAll() {
    CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    CONTAINER.stop();
  }

  @BeforeEach
  void beforeEach() {
    currentUsername = "CuratorNameHere";
    currentOrganization = randomOrganizationId();
    currentAccessRight = AccessRight.MANAGE_NVI_CANDIDATES;
    mockIdentityService(currentUsername, currentOrganization);

    CONTAINER.createIndex();
    createHandler(CONTAINER.getOpenSearchClient());
  }

  @AfterEach
  void afterEach() {
    CONTAINER.deleteIndex();
  }

  @Nested
  @DisplayName("Test access control")
  class accessControlTests {

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
  @DisplayName("Test pagination and sorting")
  class paginationTests {
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
      assertThat(actualDocumentIds).hasSize(TOTAL_DOCUMENT_COUNT).isEqualTo(expectedDocumentIds);
    }
  }

  @Nested
  @DisplayName("Test filtering by year")
  class yearTests {
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

  private static void addDocumentsToIndex(NviCandidateIndexDocument... documents) {
    CONTAINER.addDocumentsToIndex(documents);
  }

  private static void addDocumentsToIndex(Collection<NviCandidateIndexDocument> documents) {
    CONTAINER.addDocumentsToIndex(documents);
  }

  private static void assertThatResponseHasExpectedIndexDocuments(
      PaginatedSearchResult<NviCandidateIndexDocument> response,
      List<NviCandidateIndexDocument> expectedDocuments) {
    assertThatResultsAreEqual(response.getHits(), expectedDocuments);
  }

  private static void assertThatResultsAreEqual(
      List<NviCandidateIndexDocument> actualDocuments,
      List<NviCandidateIndexDocument> expectedDocuments) {
    assertThat(actualDocuments)
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("publicationDetails.contributors")
        .isEqualTo(expectedDocuments);
  }
}
