package no.sikt.nva.nvi.index.report;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.mergeCollections;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentWithApprovals;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentsForAllStatusCombinations;
import static no.sikt.nva.nvi.test.TestConstants.LAST_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.NEXT_YEAR;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.GatewayResponse.fromOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.ApprovalFactory;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.report.response.AllInstitutionsReport;
import no.sikt.nva.nvi.index.report.response.InstitutionReport;
import no.sikt.nva.nvi.index.report.response.ReportResponse;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

class FetchReportHandlerIntegrationTest {

  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static final Context CONTEXT = new FakeContext();
  private static final String PATH = "path";

  @BeforeAll
  static void startContainer() {
    CONTAINER.start();
  }

  @AfterAll
  static void stopContainer() {
    CONTAINER.stop();
  }

  private static InputStream createAllInstitutionsRequest(String period) {
    try {
      var pathParams = Map.of("period", period);
      var path = "reports/%s/institutions".formatted(period);
      return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
          .withOtherProperties(Map.of(PATH, path))
          .withPathParameters(pathParams)
          .withAccessRights(randomUri(), AccessRight.MANAGE_NVI)
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("AllInstitutionsReport with two institutions")
  class AllInstitutionsWithTwoInstitutions {

    private static final URI INSTITUTION_A = randomOrganizationId();
    private static final URI INSTITUTION_B = randomOrganizationId();

    private AllInstitutionsReport report;
    private int statusCode;
    private Map<URI, InstitutionReport> reportsByInstitution;

    @BeforeAll
    void setupAndExecute() throws IOException {
      var scenario = new TestScenario();
      setupClosedPeriod(scenario, LAST_YEAR);
      setupOpenPeriod(scenario, THIS_YEAR);
      setupFuturePeriod(scenario, NEXT_YEAR);

      CONTAINER.createIndex();

      var approvalFactoryA =
          new ApprovalFactory(INSTITUTION_A).withCreatorAffiliation(INSTITUTION_A);
      var documentsA = documentsForAllStatusCombinations(approvalFactoryA);

      var approvalFactoryB =
          new ApprovalFactory(INSTITUTION_B).withCreatorAffiliation(INSTITUTION_B);
      var documentsB =
          List.of(
              documentWithApprovals(
                  approvalFactoryB
                      .copy()
                      .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
                      .withApprovalStatus(ApprovalStatus.PENDING)
                      .build()),
              documentWithApprovals(
                  approvalFactoryB
                      .copy()
                      .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
                      .withApprovalStatus(ApprovalStatus.NEW)
                      .build()),
              documentWithApprovals(
                  approvalFactoryB
                      .copy()
                      .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
                      .withApprovalStatus(ApprovalStatus.APPROVED)
                      .build()));

      var irrelevantDocuments = createIrrelevantDocuments();
      var allDocuments =
          mergeCollections(mergeCollections(documentsA, documentsB), irrelevantDocuments);
      CONTAINER.addDocumentsToIndex(allDocuments);

      var handler =
          new FetchReportHandler(
              getHandlerEnvironment(ALLOWED_ORIGIN),
              scenario.getPeriodService(),
              CONTAINER.getReportAggregationClient());

      var output = new ByteArrayOutputStream();
      handler.handleRequest(createAllInstitutionsRequest(THIS_YEAR), output, CONTEXT);
      var gatewayResponse = fromOutputStream(output, ReportResponse.class);
      statusCode = gatewayResponse.getStatusCode();
      report = (AllInstitutionsReport) gatewayResponse.getBodyObject(ReportResponse.class);
      reportsByInstitution =
          report.institutions().stream()
              .collect(Collectors.toMap(r -> r.institution().id(), Function.identity()));
    }

    @AfterAll
    void cleanup() {
      CONTAINER.deleteIndex();
    }

    private static List<NviCandidateIndexDocument> createIrrelevantDocuments() {
      return List.of(
          createRandomIndexDocument(INSTITUTION_A, LAST_YEAR),
          createRandomIndexDocument(INSTITUTION_A, NEXT_YEAR));
    }

    @Test
    void shouldReturnOk() {
      assertEquals(200, statusCode);
    }

    @Test
    void shouldHaveTypeAllInstitutionsReport() {
      assertThat(report).isInstanceOf(AllInstitutionsReport.class);
    }

    @Test
    void shouldHaveExpectedPeriod() {
      assertThat(report.period().publishingYear()).isEqualTo(THIS_YEAR);
    }

    @Test
    void shouldHaveTwoInstitutions() {
      assertThat(report.institutions()).hasSize(2);
    }

    // Institution A: 16 total documents (4 global × 4 local statuses)
    // Disputed: 4 (DISPUTE × {NEW, PENDING, APPROVED, REJECTED})
    // Undisputed: 12 (PENDING + APPROVED + REJECTED global × 4 local)
    // Undisputed per local status: 3 each
    // Undisputed processed (APPROVED + REJECTED local): 3 + 3 = 6
    // validPoints (open period): globally approved points only

    @Test
    void shouldHaveExpectedDisputedCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().totals().disputedCount()).isEqualTo(4);
    }

    @Test
    void shouldHaveExpectedUndisputedProcessedCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().totals().undisputedProcessedCount())
          .isEqualTo(6);
    }

    @Test
    void shouldHaveExpectedUndisputedTotalCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().totals().undisputedTotalCount()).isEqualTo(12);
    }

    @Test
    void shouldHaveExpectedValidPointsForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().totals().validPoints()).isPositive();
    }

    @Test
    void shouldHaveExpectedNewCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().byLocalApprovalStatus().newCount()).isEqualTo(3);
    }

    @Test
    void shouldHaveExpectedPendingCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().byLocalApprovalStatus().pendingCount())
          .isEqualTo(3);
    }

    @Test
    void shouldHaveExpectedApprovedCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().byLocalApprovalStatus().approvedCount())
          .isEqualTo(3);
    }

    @Test
    void shouldHaveExpectedRejectedCountForInstitutionA() {
      var institutionA = reportsByInstitution.get(INSTITUTION_A);
      assertThat(institutionA.institutionSummary().byLocalApprovalStatus().rejectedCount())
          .isEqualTo(3);
    }

    // Institution B: 3 documents
    // 2 × PENDING global (PENDING + NEW local), 1 × APPROVED global (APPROVED local)
    // Disputed: 0 (no DISPUTE bucket → disputedCount should be 0)
    // Undisputed: 3 total
    // Undisputed processed: 1 (only APPROVED local)
    // validPoints (open period): points from globally APPROVED bucket

    @Test
    void shouldHaveExpectedDisputedCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().totals().disputedCount()).isZero();
    }

    @Test
    void shouldHaveExpectedUndisputedTotalCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().totals().undisputedTotalCount()).isEqualTo(3);
    }

    @Test
    void shouldHaveExpectedUndisputedProcessedCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().totals().undisputedProcessedCount())
          .isEqualTo(1);
    }

    @Test
    void shouldHaveExpectedValidPointsForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().totals().validPoints()).isPositive();
    }

    @Test
    void shouldHaveExpectedNewCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().byLocalApprovalStatus().newCount()).isEqualTo(1);
    }

    @Test
    void shouldHaveExpectedPendingCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().byLocalApprovalStatus().pendingCount())
          .isEqualTo(1);
    }

    @Test
    void shouldHaveExpectedApprovedCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().byLocalApprovalStatus().approvedCount())
          .isEqualTo(1);
    }

    @Test
    void shouldHaveExpectedRejectedCountForInstitutionB() {
      var institutionB = reportsByInstitution.get(INSTITUTION_B);
      assertThat(institutionB.institutionSummary().byLocalApprovalStatus().rejectedCount())
          .isZero();
    }

    @Test
    void shouldHaveEmptyUnitsListForAllInstitutions() {
      for (var institutionReport : report.institutions()) {
        assertThat(institutionReport.units()).isEmpty();
      }
    }

    @Test
    void shouldNotIncludeDocumentsFromOtherPeriods() {
      var allInstitutionIds =
          report.institutions().stream().map(r -> r.institution().id()).toList();
      assertThat(allInstitutionIds).containsExactlyInAnyOrder(INSTITUTION_A, INSTITUTION_B);
    }

    @Test
    void shouldHaveUnknownSectorUntilSectorAggregationIsImplemented() {
      for (var institutionReport : report.institutions()) {
        assertThat(institutionReport.sector()).isEqualTo(Sector.UNKNOWN);
      }
    }
  }
}
