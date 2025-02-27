package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.CandidateUniquenessEntryDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.model.CandidateFixtures;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
class EventBasedBatchScanHandlerTest {

  private static final int ONE_ENTRY_PER_EVENT = 1;
  private static final Map<String, String> START_FROM_BEGINNING = null;
  private static final String OUTPUT_EVENT_TOPIC = "OUTPUT_EVENT_TOPIC";
  private static final String TOPIC = new Environment().readEnv(OUTPUT_EVENT_TOPIC);
  private static final int PAGE_SIZE = 4;
  private EventBasedBatchScanHandler handler;
  private ByteArrayOutputStream output;
  private Context context;
  private FakeEventBridgeClient eventBridgeClient;
  private NviCandidateRepositoryHelper candidateRepository;
  private NviPeriodRepositoryHelper periodRepository;

  @BeforeEach
  public void init() {
    this.output = new ByteArrayOutputStream();
    this.context = mock(Context.class);
    when(context.getInvokedFunctionArn()).thenReturn(randomString());
    this.eventBridgeClient = new FakeEventBridgeClient();
    var db = initializeTestDatabase();
    candidateRepository = new NviCandidateRepositoryHelper(db);
    periodRepository = new NviPeriodRepositoryHelper(db);
    var batchScanUtil = new BatchScanUtil(candidateRepository);
    this.handler = new EventBasedBatchScanHandler(batchScanUtil, eventBridgeClient);
  }

  @Test
  void shouldNotGoIntoInfiniteLoop() {
    createPeriod();
    createRandomCandidates(100).forEach(item -> {});

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(ONE_ENTRY_PER_EVENT, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();
    assertThat(eventBridgeClient.getRequestEntries(), is(empty()));
  }

  @Test
  void shouldIterateAllCandidates() {
    createPeriod();
    var daos = generatedRepositoryCandidates();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();

    assertTrue(
        hasUpdatedVersions(daos), "All candidates should have been updated with new version");
  }

  @ParameterizedTest(name = "shouldUpdateDataEntriesWithGivenTypeWhenRequestContainsType: {0}")
  @ValueSource(
      strings = {CandidateDao.TYPE, ApprovalStatusDao.TYPE, NoteDao.TYPE, NviPeriodDao.TYPE})
  void shouldUpdateDataEntriesWithGivenTypeWhenRequestContainsType(String type) {
    createPeriod();
    var candidateDaos = generatedRepositoryCandidates();
    var approvalStatusDaos = generateCandidatesWithApprovalStatuses();
    var noteDaos = generateRepositoryCandidatesWithNotes();
    var periodDaos = periodRepository.getPeriodsDao().toList();

    var scanDatabaseRequest =
        new ScanDatabaseRequest(
            PAGE_SIZE, START_FROM_BEGINNING, List.of(KeyField.parse(type)), TOPIC);
    pushInitialEntryInEventBridge(scanDatabaseRequest);
    consumeEvents();

    assertExpectedDaosAreUpdated(type, candidateDaos, approvalStatusDaos, noteDaos, periodDaos);
  }

  @Test
  void shouldNotUpdateInitialDbCandidate() {
    createPeriod();
    var dao =
        Optional.ofNullable(
                candidateRepository.create(randomDbCandidate(), List.of(), Year.now().toString()))
            .map(CandidateDao::identifier)
            .map(candidateRepository::findDaoById)
            .orElseThrow();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();
    var updated = candidateRepository.findDaoById(dao.identifier());

    assertEquals(dao.candidate(), updated.candidate());
  }

  @Test
  void shouldIterateAllNotes() {
    createPeriod();
    var daos = generateRepositoryCandidatesWithNotes();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();

    assertTrue(hasUpdatedVersions(daos), "All notes should have been updated with new version");
  }

  @Test
  void shouldIterateAllApprovals() {
    createPeriod();
    var daos = generateCandidatesWithApprovalStatuses();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();

    assertTrue(hasUpdatedVersions(daos), "All approvals should have been updated with new version");
  }

  @Test
  void shouldIteratePeriodEntries() {
    createPeriod();
    createRandomCandidates(10).forEach(item -> {});

    var items = periodRepository.getPeriodsDao().toList();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();

    assertTrue(
        hasUpdatedVersions(items), "All period entries should have been updated with new version");
  }

  @Test
  void shouldNotIterateUniquenessEntries() {
    createPeriod();
    createRandomCandidates(10).forEach(item -> {});

    var items = candidateRepository.getUniquenessEntries().toList();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();

    var noEntitiesUpdated =
        items.stream().allMatch(this::isSameVersionAsRepositoryCopy) && !items.isEmpty();

    assertTrue(
        noEntitiesUpdated, "No uniqueness entries should have been updated with new version");
  }

  @Test
  void bodyShouldNotChange() {
    createPeriod();
    var candidates = createRandomCandidates(10).toList();

    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();

    var noEntitiesUpdated =
        candidates.stream().allMatch(this::isSameBodyAsRepositoryCopy) && !candidates.isEmpty();

    assertTrue(noEntitiesUpdated, "No values should have been updated with new version");
  }

  @Test
  void emptyResultShouldNotFail() throws IOException {
    pushInitialEntryInEventBridge(
        new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, null, TOPIC));

    consumeEvents();
    var result = JsonUtils.dtoObjectMapper.readValue(output.toByteArray(), ListingResult.class);

    assertThat(result.getTotalItemCount(), is(0));
  }

  private static DbCandidate randomDbCandidate() {
    return DbCandidate.builder()
        .publicationId(randomUri())
        .reportStatus(ReportStatus.REPORTED)
        .level(DbLevel.LEVEL_ONE)
        .channelType(ChannelType.JOURNAL)
        .publicationDate(
            new DbPublicationDate(String.valueOf(LocalDate.now().getYear()), null, null))
        .build();
  }

  private void assertExpectedDaosAreUpdated(
      String type,
      List<CandidateDao> candidateDaos,
      List<ApprovalStatusDao> approvalStatusDaos,
      List<NoteDao> noteDaos,
      List<NviPeriodDao> periodDaos) {
    switch (type) {
      case CandidateDao.TYPE ->
          assertOnlyCandidatesUpdated(candidateDaos, approvalStatusDaos, noteDaos, periodDaos);
      case ApprovalStatusDao.TYPE ->
          assertOnlyAprovalsUpdated(candidateDaos, approvalStatusDaos, noteDaos, periodDaos);
      case NoteDao.TYPE ->
          assertOnlyNotesUpdated(candidateDaos, approvalStatusDaos, noteDaos, periodDaos);
      case NviPeriodDao.TYPE ->
          assertOnlyPeriodsUpdated(candidateDaos, approvalStatusDaos, noteDaos, periodDaos);
      default -> throw new IllegalArgumentException("Unknown type: " + type);
    }
  }

  private void assertOnlyPeriodsUpdated(
      List<CandidateDao> candidateDaos,
      List<ApprovalStatusDao> approvalStatusDaos,
      List<NoteDao> noteDaos,
      List<NviPeriodDao> periodDaos) {
    assertTrue(hasSameVerions(candidateDaos));
    assertTrue(hasSameVerions(approvalStatusDaos));
    assertTrue(hasSameVerions(noteDaos));
    assertTrue(hasUpdatedVersions(periodDaos));
  }

  private void assertOnlyNotesUpdated(
      List<CandidateDao> candidateDaos,
      List<ApprovalStatusDao> approvalStatusDaos,
      List<NoteDao> noteDaos,
      List<NviPeriodDao> periodDaos) {
    assertTrue(hasSameVerions(candidateDaos));
    assertTrue(hasSameVerions(approvalStatusDaos));
    assertTrue(hasUpdatedVersions(noteDaos));
    assertTrue(hasSameVerions(periodDaos));
  }

  private void assertOnlyAprovalsUpdated(
      List<CandidateDao> candidateDaos,
      List<ApprovalStatusDao> approvalStatusDaos,
      List<NoteDao> noteDaos,
      List<NviPeriodDao> periodDaos) {
    assertTrue(hasSameVerions(candidateDaos));
    assertTrue(hasUpdatedVersions(approvalStatusDaos));
    assertTrue(hasSameVerions(noteDaos));
    assertTrue(hasSameVerions(periodDaos));
  }

  private void assertOnlyCandidatesUpdated(
      List<CandidateDao> candidateDaos,
      List<ApprovalStatusDao> approvalStatusDaos,
      List<NoteDao> noteDaos,
      List<NviPeriodDao> periodDaos) {
    assertTrue(hasUpdatedVersions(candidateDaos));
    assertTrue(hasSameVerions(approvalStatusDaos));
    assertTrue(hasSameVerions(noteDaos));
    assertTrue(hasSameVerions(periodDaos));
  }

  private boolean hasUpdatedVersions(List<? extends Dao> daos) {
    return daos.stream().noneMatch(this::isSameVersionAsRepositoryCopy);
  }

  private boolean hasSameVerions(List<? extends Dao> daos) {
    return daos.stream().allMatch(this::isSameVersionAsRepositoryCopy);
  }

  private void createPeriod() {
    periodRepository.save(
        DbNviPeriod.builder()
            .publishingYear(String.valueOf(Year.now().getValue()))
            .startDate(Instant.now())
            .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
            .build());
  }

  private void consumeEvents() {
    while (thereAreMoreEventsInEventBridge()) {
      var currentRequest = consumeLatestEmittedEvent();
      handler.handleRequest(eventToInputStream(currentRequest), output, context);
    }
  }

  private List<ApprovalStatusDao> generateCandidatesWithApprovalStatuses() {
    return createRandomCandidates(10)
        .map(Candidate::getIdentifier)
        .flatMap(candidateRepository::findApprovalDaosByCandidateId)
        .toList();
  }

  private List<CandidateDao> generatedRepositoryCandidates() {
    return createRandomCandidates(10)
        .map(Candidate::getIdentifier)
        .map(candidateRepository::findDaoById)
        .toList();
  }

  private List<NoteDao> generateRepositoryCandidatesWithNotes() {
    return createRandomCandidates(10)
        .map(Candidate::getIdentifier)
        .flatMap(candidateRepository::findNoteDaosByCandidateId)
        .toList();
  }

  private boolean isSameBodyAsRepositoryCopy(Candidate candidate) {
    // TODO: should replace this comparison with the actual data field (equals in CandidateBO?)
    var mockUriRetriever = mock(UriRetriever.class);
    var mockOrganizationRetriever = new OrganizationRetriever(mockUriRetriever);
    var userOrganizationId = candidate.getInstitutionPoints().getFirst().institutionId();

    var originalDto = candidate.toDto(userOrganizationId, mockOrganizationRetriever);
    var repositoryCopy =
        Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository);
    var repositoryDto = repositoryCopy.toDto(userOrganizationId, mockOrganizationRetriever);
    return originalDto.equals(repositoryDto);
  }

  private boolean isSameVersionAsRepositoryCopy(Dao dao) {
    var persistedDao = getPersistedDao(dao);
    return Objects.equals(dao.version(), persistedDao.version());
  }

  private Dao getPersistedDao(Dao dao) {
    return switch (dao) {
      case CandidateDao candidateDao -> candidateRepository.findDaoById(candidateDao.identifier());
      case ApprovalStatusDao approvalStatusDao ->
          candidateRepository.findApprovalDaoByIdAndInstitutionId(
              approvalStatusDao.identifier(), approvalStatusDao.approvalStatus().institutionId());
      case NoteDao noteDao ->
          candidateRepository.getNoteDaoById(noteDao.identifier(), noteDao.note().noteId());
      case NviPeriodDao nviPeriodDao ->
          periodRepository.findDaoByPublishingYear(nviPeriodDao.nviPeriod().publishingYear());
      case CandidateUniquenessEntryDao candidateUniquenessEntryDao ->
          candidateRepository.getUniquenessEntry(candidateUniquenessEntryDao);
      case null, default -> throw new IllegalArgumentException("Unknown type: " + dao);
    };
  }

  private ScanDatabaseRequest consumeLatestEmittedEvent() {
    var allRequests = eventBridgeClient.getRequestEntries();
    var latest = allRequests.remove(allRequests.size() - 1);
    return attempt(() -> ScanDatabaseRequest.fromJson(latest.detail())).orElseThrow();
  }

  private boolean thereAreMoreEventsInEventBridge() {
    return !eventBridgeClient.getRequestEntries().isEmpty();
  }

  private void pushInitialEntryInEventBridge(ScanDatabaseRequest scanDatabaseRequest) {
    var entry = PutEventsRequestEntry.builder().detail(scanDatabaseRequest.toJsonString()).build();
    eventBridgeClient.getRequestEntries().add(entry);
  }

  private Stream<Candidate> createRandomCandidates(int i) {
    return IntStream.range(0, i)
        .boxed()
        .map(
            item ->
                CandidateFixtures.randomApplicableCandidate(candidateRepository, periodRepository))
        .map(
            a ->
                a.createNote(
                    new CreateNoteRequest(randomString(), randomString(), randomUri()),
                    candidateRepository));
  }

  private InputStream eventToInputStream(ScanDatabaseRequest scanDatabaseRequest) {
    var event = new AwsEventBridgeEvent<ScanDatabaseRequest>();
    event.setAccount(randomString());
    event.setVersion(randomString());
    event.setSource(randomString());
    event.setRegion(randomElement(Region.regions()));
    event.setDetail(scanDatabaseRequest);
    return IoUtils.stringToStream(event.toJsonString());
  }

  protected static class NviCandidateRepositoryHelper extends CandidateRepository {

    public NviCandidateRepositoryHelper(DynamoDbClient client) {
      super(client);
    }

    public CandidateDao findDaoById(UUID id) {
      return Optional.of(CandidateDao.builder().identifier(id).build())
          .map(candidateTable::getItem)
          .orElseThrow();
    }

    public Stream<NoteDao> findNoteDaosByCandidateId(UUID id) {
      return noteTable.query(queryCandidateParts(id, NoteDao.TYPE)).items().stream();
    }

    public Stream<ApprovalStatusDao> findApprovalDaosByCandidateId(UUID identifier) {
      return approvalStatusTable
          .query(queryCandidateParts(identifier, ApprovalStatusDao.TYPE))
          .items()
          .stream();
    }

    public NoteDao getNoteDaoById(UUID candidateIdentifier, UUID noteIdentifier) {
      return Optional.of(noteKey(candidateIdentifier, noteIdentifier))
          .map(noteTable::getItem)
          .orElseThrow();
    }

    public ApprovalStatusDao findApprovalDaoByIdAndInstitutionId(UUID identifier, URI uri) {
      return approvalStatusTable
          .query(findApprovalByCandidateIdAndInstitutionId(identifier, uri))
          .items()
          .stream()
          .findFirst()
          .orElseThrow();
    }

    public Stream<CandidateUniquenessEntryDao> getUniquenessEntries() {
      return uniquenessTable.scan().items().stream().filter(a -> a.partitionKey() != null);
    }

    public CandidateUniquenessEntryDao getUniquenessEntry(CandidateUniquenessEntryDao entry) {
      return uniquenessTable.getItem(entry);
    }

    private static QueryConditional findApprovalByCandidateIdAndInstitutionId(
        UUID identifier, URI uri) {
      return QueryConditional.keyEqualTo(approvalByCandidateIdAndInstitutionIdKey(identifier, uri));
    }

    private static Key approvalByCandidateIdAndInstitutionIdKey(UUID identifier, URI uri) {
      return Key.builder()
          .partitionValue(CandidateDao.createPartitionKey(identifier.toString()))
          .sortValue(ApprovalStatusDao.createSortKey(uri.toString()))
          .build();
    }
  }

  protected static class NviPeriodRepositoryHelper extends PeriodRepository {

    public NviPeriodRepositoryHelper(DynamoDbClient client) {
      super(client);
    }

    public Stream<NviPeriodDao> getPeriodsDao() {
      return nviPeriodTable.query(beginsWithPeriodQuery()).stream()
          .map(Page::items)
          .flatMap(Collection::stream);
    }

    public NviPeriodDao findDaoByPublishingYear(String publishingYear) {
      var queryObj =
          NviPeriodDao.builder()
              .nviPeriod(DbNviPeriod.builder().publishingYear(publishingYear).build())
              .identifier(publishingYear)
              .build();

      return this.nviPeriodTable.getItem(queryObj);
    }
  }
}
