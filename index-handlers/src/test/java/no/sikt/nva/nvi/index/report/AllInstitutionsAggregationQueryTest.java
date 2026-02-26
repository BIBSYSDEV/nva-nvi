package no.sikt.nva.nvi.index.report;

import static java.util.Collections.emptyList;
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
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.ApprovalFactory;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import no.sikt.nva.nvi.index.report.query.AllInstitutionsQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

class AllInstitutionsAggregationQueryTest {

  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();

  @BeforeAll
  static void beforeAll() {
    CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    CONTAINER.stop();
  }

  private static List<InstitutionAggregationResult> getReports(NviPeriod period) {
    try {
      var client = CONTAINER.getReportAggregationClient();
      return client.executeQuery(new AllInstitutionsQuery(period));
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  private static Map<URI, InstitutionAggregationResult> mapReportsToInstitution(
      List<InstitutionAggregationResult> reports) {
    return reports.stream()
        .collect(
            Collectors.toMap(InstitutionAggregationResult::institutionId, Function.identity()));
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  @DisplayName("Institution with one candidate for each status combination")
  class SingleInstitutionScenario {

    private static final URI INSTITUTION_ID = randomOrganizationId();
    private static final Sector SECTOR = Sector.INSTITUTE;
    private static final int CANDIDATES_PER_GLOBAL_STATUS = 4;
    private List<NviCandidateIndexDocument> relevantDocuments;
    private NviPeriodService periodService;
    private TestScenario scenario;

    private Map<URI, InstitutionAggregationResult> reportsByInstitution;
    private InstitutionAggregationResult institutionReport;

    @BeforeAll
    void setupAndExecute() {
      scenario = new TestScenario();
      periodService = scenario.getPeriodService();
      setupClosedPeriod(scenario, LAST_YEAR);
      setupOpenPeriod(scenario, THIS_YEAR);
      setupFuturePeriod(scenario, NEXT_YEAR);

      CONTAINER.createIndex();

      var approvalFactory =
          new ApprovalFactory(INSTITUTION_ID)
              .withCreatorAffiliation(INSTITUTION_ID)
              .withSector(SECTOR);
      relevantDocuments = documentsForAllStatusCombinations(approvalFactory);

      var irrelevantDocuments = createIrrelevantDocuments();
      var allDocuments = mergeCollections(relevantDocuments, irrelevantDocuments);
      CONTAINER.addDocumentsToIndex(allDocuments);

      var reports = getReports(periodService.getByPublishingYear(THIS_YEAR));
      reportsByInstitution = mapReportsToInstitution(reports);
      institutionReport = reportsByInstitution.get(INSTITUTION_ID);
    }

    @AfterAll
    void cleanup() {
      CONTAINER.deleteIndex();
    }

    private static List<NviCandidateIndexDocument> createIrrelevantDocuments() {
      var fromOtherOrganization = createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR);
      var fromLastYear = createRandomIndexDocument(INSTITUTION_ID, CURRENT_YEAR - 1);
      var fromNextYear = createRandomIndexDocument(INSTITUTION_ID, CURRENT_YEAR + 1);
      return List.of(fromOtherOrganization, fromLastYear, fromNextYear);
    }

    @Test
    void shouldIncludeExpectedPeriodInReport() {
      assertThat(institutionReport.period().hasPublishingYear(THIS_YEAR)).isTrue();
    }

    @Test
    void shouldIncludeExpectedSectorInReport() {
      assertThat(institutionReport.sector()).isEqualTo(SECTOR.toString());
    }

    @Test
    void shouldHaveExpectedValidPoints() {
      var approvedSummary = institutionReport.byGlobalStatus().get(GlobalApprovalStatus.APPROVED);
      assertThat(institutionReport.validPoints()).isEqualTo(approvedSummary.totalPoints());
      assertThat(institutionReport.validPoints()).isPositive();
    }

    @Test
    void shouldHaveExpectedCandidateCountForRejectedStatus() {
      var rejectedSummary = institutionReport.byGlobalStatus().get(GlobalApprovalStatus.REJECTED);
      assertThat(rejectedSummary.totalCount()).isEqualTo(CANDIDATES_PER_GLOBAL_STATUS);
    }

    @Test
    void shouldHaveExpectedDisputedCount() {
      assertThat(institutionReport.disputedCount()).isEqualTo(4);
    }

    @Test
    void shouldHaveExpectedUndisputedProcessedCount() {
      assertThat(institutionReport.undisputedProcessedCount()).isEqualTo(6);
    }

    @Test
    void shouldHaveExpectedUndisputedTotalCount() {
      assertThat(institutionReport.undisputedTotalCount()).isEqualTo(12);
    }

    @Test
    void shouldExcludeDisputedCandidatesFromLocalStatusSummary() {
      var expectedCountPerStatus = 3;

      var undisputedByLocalStatus = institutionReport.undisputed();
      assertThat(undisputedByLocalStatus.forStatus(ApprovalStatus.NEW).candidateCount())
          .isEqualTo(expectedCountPerStatus);
      assertThat(undisputedByLocalStatus.forStatus(ApprovalStatus.PENDING).candidateCount())
          .isEqualTo(expectedCountPerStatus);
      assertThat(undisputedByLocalStatus.forStatus(ApprovalStatus.REJECTED).candidateCount())
          .isEqualTo(expectedCountPerStatus);
      assertThat(undisputedByLocalStatus.forStatus(ApprovalStatus.APPROVED).candidateCount())
          .isEqualTo(expectedCountPerStatus);
    }
  }

  private NviCandidateIndexDocument candidate(
      ApprovalFactory factory, GlobalApprovalStatus globalStatus, ApprovalStatus localStatus) {
    return documentWithApprovals(
        factory
            .copy()
            .withGlobalApprovalStatus(globalStatus)
            .withApprovalStatus(localStatus)
            .build());
  }

  /**
   * Creates one candidate for each legal combination of global and local approval status:
   *
   * <ul>
   *   <li>DISPUTE + NEW
   *   <li>DISPUTE + PENDING
   *   <li>DISPUTE + APPROVED
   *   <li>DISPUTE + REJECTED
   *   <li>PENDING + NEW
   *   <li>PENDING + PENDING
   *   <li>PENDING + APPROVED
   *   <li>PENDING + REJECTED
   *   <li>APPROVED + APPROVED
   *   <li>REJECTED + REJECTED
   * </ul>
   */
  private List<NviCandidateIndexDocument> createCandidatesForAllLegalStatusCombinations(
      ApprovalFactory factory) {
    return List.of(
        candidate(factory, GlobalApprovalStatus.DISPUTE, ApprovalStatus.NEW),
        candidate(factory, GlobalApprovalStatus.DISPUTE, ApprovalStatus.PENDING),
        candidate(factory, GlobalApprovalStatus.DISPUTE, ApprovalStatus.APPROVED),
        candidate(factory, GlobalApprovalStatus.DISPUTE, ApprovalStatus.REJECTED),
        candidate(factory, GlobalApprovalStatus.PENDING, ApprovalStatus.NEW),
        candidate(factory, GlobalApprovalStatus.PENDING, ApprovalStatus.PENDING),
        candidate(factory, GlobalApprovalStatus.PENDING, ApprovalStatus.APPROVED),
        candidate(factory, GlobalApprovalStatus.PENDING, ApprovalStatus.REJECTED),
        candidate(factory, GlobalApprovalStatus.APPROVED, ApprovalStatus.APPROVED),
        candidate(factory, GlobalApprovalStatus.REJECTED, ApprovalStatus.REJECTED));
  }

  /**
   * Creates one candidate for each legal combination of global and local approval status:
   *
   * <ul>
   *   <li>NEW => Pending
   *   <li>DISPUTE + PENDING
   *   <li>DISPUTE + APPROVED
   *   <li>DISPUTE + REJECTED
   *   <li>PENDING + NEW
   *   <li>PENDING + PENDING
   *   <li>PENDING + APPROVED
   *   <li>PENDING + REJECTED
   *   <li>APPROVED + APPROVED
   *   <li>REJECTED + REJECTED
   * </ul>
   */
  public static List<NviCandidateIndexDocument> candidatesForSingleInstitution(
      ApprovalFactory institution) {
    var documents = new ArrayList<NviCandidateIndexDocument>();
    var approvals = legalApprovalStatusCombinationsForSingleInstitution(institution);
    for (var globalStatus : GlobalApprovalStatus.values()) {
      for (var status : ApprovalStatus.values()) {
        var approval =
            institution
                .copy()
                .withApprovalStatus(status)
                .withGlobalApprovalStatus(globalStatus)
                .build();
        documents.add(documentWithApprovals(approval));
      }
    }
    return documents;
  }

  private static List<ApprovalView> legalApprovalStatusCombinationsForSingleInstitution(
      ApprovalFactory institution) {
    return List.of(
        approval(institution, GlobalApprovalStatus.PENDING, ApprovalStatus.NEW),
        approval(institution, GlobalApprovalStatus.PENDING, ApprovalStatus.PENDING),
        approval(institution, GlobalApprovalStatus.APPROVED, ApprovalStatus.APPROVED),
        approval(institution, GlobalApprovalStatus.REJECTED, ApprovalStatus.REJECTED));
  }

  private static List<ApprovalView> legalApprovalStatusCombinationsForCollaborations(
      ApprovalFactory institution) {
    return List.of(
        approval(institution, GlobalApprovalStatus.PENDING, ApprovalStatus.NEW),
        approval(institution, GlobalApprovalStatus.PENDING, ApprovalStatus.PENDING),
        approval(institution, GlobalApprovalStatus.APPROVED, ApprovalStatus.APPROVED),
        approval(institution, GlobalApprovalStatus.REJECTED, ApprovalStatus.REJECTED));
  }

  public static ApprovalView approval(
      ApprovalFactory institutionApproval,
      GlobalApprovalStatus globalStatus,
      ApprovalStatus approvalStatus) {
    return institutionApproval
        .copy()
        .withGlobalApprovalStatus(globalStatus)
        .withApprovalStatus(approvalStatus)
        .build();
  }

  /**
   * Creates one candidate for each legal combination of global and local approval status:
   *
   * <ul>
   *   <li>DISPUTE + NEW
   *   <li>DISPUTE + PENDING
   *   <li>DISPUTE + APPROVED
   *   <li>DISPUTE + REJECTED
   *   <li>PENDING + NEW
   *   <li>PENDING + PENDING
   *   <li>PENDING + APPROVED
   *   <li>PENDING + REJECTED
   *   <li>APPROVED + APPROVED
   *   <li>REJECTED + REJECTED
   * </ul>
   */
  public static List<NviCandidateIndexDocument> candidatesForCollaboration() {
    return emptyList();
  }

  private List<NviCandidateIndexDocument> createCollaborationCandidates(
      ApprovalFactory factoryA, ApprovalFactory factoryB) {
    var collaborationPending =
        documentWithApprovals(
            factoryA
                .copy()
                .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
                .withApprovalStatus(ApprovalStatus.APPROVED)
                .build(),
            factoryB
                .copy()
                .withGlobalApprovalStatus(GlobalApprovalStatus.PENDING)
                .withApprovalStatus(ApprovalStatus.NEW)
                .build());

    var collaborationApproved =
        documentWithApprovals(
            factoryA
                .copy()
                .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
                .withApprovalStatus(ApprovalStatus.APPROVED)
                .build(),
            factoryB
                .copy()
                .withGlobalApprovalStatus(GlobalApprovalStatus.APPROVED)
                .withApprovalStatus(ApprovalStatus.APPROVED)
                .build());

    return List.of(collaborationPending, collaborationApproved);
  }
}
