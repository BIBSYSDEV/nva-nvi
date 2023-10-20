package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
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
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.CandidateUniquenessEntryDao;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.ListingResult;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

class EventBasedBatchScanHandlerTest extends LocalDynamoTest {

    public static final int ONE_ENTRY_PER_EVENT = 1;
    public static final Map<String, String> START_FROM_BEGINNING = null;
    public static final String OUTPUT_EVENT_TOPIC = "OUTPUT_EVENT_TOPIC";
    public static final String TOPIC = new Environment().readEnv(OUTPUT_EVENT_TOPIC);
    public static final int PAGE_SIZE = 4;
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
        var nviService = new NviService(periodRepository, candidateRepository);
        this.handler = new EventBasedBatchScanHandler(nviService, eventBridgeClient);
    }

    @Test
    void shouldNotGoIntoInfiniteLoop() {
        createPeriod();
        createRandomCandidates(100).forEach(item -> {
        });

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(ONE_ENTRY_PER_EVENT, START_FROM_BEGINNING, TOPIC));

        consumeEvents();
        assertThat(eventBridgeClient.getRequestEntries(), is(empty()));
    }

    @Test
    void shouldIterateAllCandidates() {
        createPeriod();
        var daos = generatedRepositoryCandidates();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();

        var allEntitiesUpdated = daos.stream().noneMatch(this::isSameVersionAsRepositoryCopy);

        assertTrue(allEntitiesUpdated, "All candidates should have been updated with new version");
    }

    @Test
    void shouldIterateAllNotes() {
        createPeriod();
        var daos = generateRepositoryCandidatesWithNotes();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();

        var allEntitiesUpdated = daos.stream().noneMatch(this::isSameVersionAsRepositoryCopy);

        assertTrue(allEntitiesUpdated, "All notes should have been updated with new version");
    }

    @Test
    void shouldIterateAllApprovals() {
        createPeriod();
        var daos = generateCandidatesWithApprovalStatuses();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();

        var allEntitiesUpdated = daos.stream().noneMatch(this::isSameVersionAsRepositoryCopy);

        assertTrue(allEntitiesUpdated, "All approvals should have been updated with new version");
    }

    @Test
    void shouldIteratePeriodEntries() {
        createPeriod();
        createRandomCandidates(10).forEach(item -> {
        });

        var items = periodRepository.getPeriodsDao().toList();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();

        var allEntitiesUpdated = items.stream().noneMatch(this::isSameVersionAsRepositoryCopy);

        assertTrue(allEntitiesUpdated, "All period entries should have been updated with new version");
    }

    @Test
    void shouldNotIterateUniquenessEntries() {
        createPeriod();
        createRandomCandidates(10).forEach(item -> {
        });

        var items = candidateRepository.getUniquenessEntries().toList();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();

        var noEntitiesUpdated = items.stream().allMatch(this::isSameVersionAsRepositoryCopy) && !items.isEmpty();

        assertTrue(noEntitiesUpdated, "No uniqueness entries should have been updated with new version");
    }

    @Test
    void bodyShouldNotChange() {
        createPeriod();
        var candidates = createRandomCandidates(10).toList();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();

        var noEntitiesUpdated = candidates.stream().allMatch(this::isSameBodyAsRepositoryCopy) && !candidates.isEmpty();

        assertTrue(noEntitiesUpdated, "No values should have been updated with new version");
    }

    @Test
    void emptyResultShouldNotFail() throws IOException {
        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        consumeEvents();
        var result = JsonUtils.dtoObjectMapper.readValue(output.toByteArray(), ListingResult.class);

        assertThat(result.totalItem(), is(0));
        assertThat(result.unprocessedItemsForTable(), is(0));
    }

    private void createPeriod() {
        periodRepository.save(DbNviPeriod.builder()
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
        return createRandomCandidates(10).map(CandidateBO::getIdentifier)
                   .flatMap(candidateRepository::findApprovalDaosByCandidateId)
                   .toList();
    }

    private List<CandidateDao> generatedRepositoryCandidates() {
        return createRandomCandidates(10).map(CandidateBO::getIdentifier)
                   .map(candidateRepository::findDaoById)
                   .toList();
    }

    private List<NoteDao> generateRepositoryCandidatesWithNotes() {
        return createRandomCandidates(10).map(CandidateBO::getIdentifier)
                   .flatMap(candidateRepository::findNoteDaosByCandidateId)
                   .toList();
    }

    private boolean isSameBodyAsRepositoryCopy(CandidateBO candidate) {
        //TODO: should replace this comparison with the actual data field (equals in CandidateBO?)
        return candidate.toDto()
                   .equals(CandidateBO.fromRequest(candidate::getIdentifier, candidateRepository, periodRepository)
                               .toDto());
    }

    private boolean isSameVersionAsRepositoryCopy(CandidateDao dao) {
        return Objects.equals(dao.version(), candidateRepository.findDaoById(dao.identifier()).version());
    }

    private boolean isSameVersionAsRepositoryCopy(CandidateUniquenessEntryDao item) {
        return Objects.equals(item.version(), candidateRepository.getUniquenessEntry(item).version());
    }

    private boolean isSameVersionAsRepositoryCopy(ApprovalStatusDao dao) {
        return Objects.equals(dao.version(),
                              candidateRepository.findApprovalDaoByIdAndInstitutionId(dao.identifier(),
                                                                                      dao.approvalStatus()
                                                                                          .institutionId())
                                  .version());
    }

    private boolean isSameVersionAsRepositoryCopy(NviPeriodDao item) {
        return Objects.equals(item.version(),
                              periodRepository.findDaoByPublishingYear(item.nviPeriod().publishingYear())
                                  .version());
    }

    private boolean isSameVersionAsRepositoryCopy(NoteDao dao) {
        return Objects.equals(dao.version(),
                              candidateRepository.getNoteDaoById(dao.identifier(), dao.note().noteId()).version());
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

    private Stream<CandidateBO> createRandomCandidates(int i) {
        return IntStream.range(0, i)
                   .boxed()
                   .map(item -> CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                                        periodRepository))
                   .map(Optional::orElseThrow)
                   .map(a -> a.createNote(new CreateNoteRequest(randomString(), randomString())));
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
            return approvalStatusTable.query(queryCandidateParts(identifier, ApprovalStatusDao.TYPE)).items().stream();
        }

        public NoteDao getNoteDaoById(UUID candidateIdentifier, UUID noteIdentifier) {
            return Optional.of(noteKey(candidateIdentifier, noteIdentifier)).map(noteTable::getItem).orElseThrow();
        }

        public ApprovalStatusDao findApprovalDaoByIdAndInstitutionId(UUID identifier, URI uri) {
            return approvalStatusTable.query(findApprovalByCandidateIdAndInstitutionId(identifier, uri))
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

        private static QueryConditional findApprovalByCandidateIdAndInstitutionId(UUID identifier, URI uri) {
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
            return nviPeriodTable.query(beginsWithPeriodQuery()).stream().map(Page::items).flatMap(Collection::stream);
        }

        public NviPeriodDao findDaoByPublishingYear(String publishingYear) {
            var queryObj = NviPeriodDao.builder()
                               .nviPeriod(DbNviPeriod.builder().publishingYear(publishingYear).build())
                               .identifier(publishingYear)
                               .build();

            return this.nviPeriodTable.getItem(queryObj);
        }
    }
}