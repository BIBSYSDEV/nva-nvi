package no.sikt.nva.nvi.common.db;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR;
import static no.sikt.nva.nvi.common.DatabaseConstants.VERSION_FIELD;
import static no.sikt.nva.nvi.common.db.PeriodRepository.createPeriodKey;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static no.sikt.nva.nvi.common.utils.Validator.hasElements;
import static software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromImmutableClass;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.keyEqualTo;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.sortBeginsWith;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.DatabaseConstants;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.sikt.nva.nvi.common.model.ListingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest.Builder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class CandidateRepository extends DynamoRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateRepository.class);
  public static final int DEFAULT_PAGE_SIZE = 100;
  private static final int BATCH_SIZE = 25;
  private static final long INITIAL_RETRY_WAIT_TIME_MS = 1000;
  protected final DynamoDbTable<CandidateDao> candidateTable;
  protected final DynamoDbTable<CandidateUniquenessEntryDao> uniquenessTable;
  protected final DynamoDbTable<ApprovalStatusDao> approvalStatusTable;
  protected final DynamoDbTable<NoteDao> noteTable;
  protected final DynamoDbTable<NviPeriodDao> periodTable;
  private final DynamoDbIndex<CandidateDao> publicationIdIndex;
  private final DynamoDbIndex<CandidateDao> yearIndex;
  private final DynamoDbRetryWrapper dynamoDbRetryClient;

  public CandidateRepository(DynamoDbClient client) {
    super(client);
    this.candidateTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateDao.class));
    this.uniquenessTable =
        this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateUniquenessEntryDao.class));
    this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
    this.yearIndex = this.candidateTable.index(SECONDARY_INDEX_YEAR);
    this.approvalStatusTable =
        this.client.table(NVI_TABLE_NAME, fromImmutableClass(ApprovalStatusDao.class));
    this.noteTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(NoteDao.class));
    this.periodTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(NviPeriodDao.class));
    this.dynamoDbRetryClient =
        DynamoDbRetryWrapper.builder()
            .dynamoDbClient(defaultClient)
            .initialRetryWaitTimeMs(INITIAL_RETRY_WAIT_TIME_MS)
            .tableName(NVI_TABLE_NAME)
            .build();
  }

  public ListingResult<Dao> scanEntries(
      int pageSize, Map<String, String> startMarker, List<KeyField> types) {
    var scan = defaultClient.scan(createScanRequest(pageSize, startMarker, types));
    var items = extractDatabaseEntries(scan);
    return new ListingResult<>(
        thereAreMorePagesToScan(scan), toStringMap(scan.lastEvaluatedKey()), scan.count(), items);
  }

  public void writeEntries(List<Dao> items) {
    var writeRequests = createWriteRequestsForBatchJob(items);
    var batches = getBatches(writeRequests);
    batches.forEach(batch -> dynamoDbRetryClient.batchWriteItem(toBatchRequest(batch)));
  }

  /**
   * Queries candidates by year using a GSI (not strongly consistent). Suitable for bulk operations
   * that extract identifiers but may not return the latest data.
   */
  public ListingResult<CandidateDao> fetchCandidatesByYear(
      String year,
      boolean includeReportedCandidates,
      Integer pageSize,
      Map<String, String> startMarker) {
    var page = queryYearIndex(year, pageSize, startMarker);
    var lastEvaluatedKey =
        Optional.ofNullable(page)
            .map(Page::lastEvaluatedKey)
            .map(CandidateRepository::toStringMap)
            .orElse(emptyMap());
    var items = Optional.ofNullable(page).map(Page::items).orElse(emptyList());
    return new ListingResult<>(
        thereAreMorePagesToScan(page),
        lastEvaluatedKey,
        items.size(),
        includeReportedCandidates ? items : filterOutReportedCandidates(items));
  }

  /**
   * Deprecated method used only for importing historical data via CristinNviReportEventConsumer.
   * This method will be removed once the historical data import is complete.
   *
   * @deprecated This method is only for legacy data import.
   */
  @Deprecated(forRemoval = true, since = "2025-11-01")
  public void create(
      DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses, String year) {
    var identifier = randomUUID();
    var candidate =
        CandidateDao.builder()
            .identifier(identifier)
            .candidate(dbCandidate)
            .periodYear(year)
            .build();
    var approvals = approvalStatuses.stream().map(approval -> approval.toDao(identifier)).toList();
    create(null, candidate, approvals);
  }

  /**
   * Creates a new NVI candidate with associated approvals.
   *
   * @param dependentPeriod Period the candidate belongs to. Transaction fails if modified
   *     concurrently.
   * @param candidate Candidate to create
   * @param approvals Approval statuses to create
   */
  public void create(
      NviPeriodDao dependentPeriod,
      CandidateDao candidate,
      Collection<ApprovalStatusDao> approvals) {
    LOGGER.info("Creating new candidate with identifier={}", candidate.identifier());
    var transaction = TransactWriteItemsEnhancedRequest.builder();

    var publicationId = candidate.candidate().publicationId();
    var uniquenessEntry = new CandidateUniquenessEntryDao(publicationId.toString());

    if (nonNull(dependentPeriod)) {
      transaction.addConditionCheck(periodTable, requireExpectedPeriodRevision(dependentPeriod));
    }
    addNewItemWithVersion(transaction, uniquenessTable, uniquenessEntry);
    addNewItemWithVersion(transaction, candidateTable, candidate);

    for (var approval : approvals) {
      addNewItemWithVersion(transaction, approvalStatusTable, approval);
    }

    sendTransaction(transaction.build());
  }

  /**
   * Updates a candidate with its approvals and notes.
   *
   * @param dependentPeriods Periods the candidate belongs to. Transaction fails if any are modified
   *     concurrently.
   * @param candidate Candidate to update
   * @param approvalsToUpdate Approval statuses to update
   * @param approvalsToDelete Approval statuses to delete
   * @param notesToUpdate Notes to update
   */
  public void updateCandidateAggregate(
      Collection<NviPeriodDao> dependentPeriods,
      CandidateDao candidate,
      Collection<ApprovalStatusDao> approvalsToUpdate,
      Collection<ApprovalStatusDao> approvalsToDelete,
      Collection<NoteDao> notesToUpdate) {
    LOGGER.info("Updating candidate {}", candidate.identifier());
    var transaction = TransactWriteItemsEnhancedRequest.builder();
    for (var period : dependentPeriods) {
      transaction.addConditionCheck(periodTable, requireExpectedPeriodRevision(period));
    }
    addUpdatedItemWithVersion(transaction, candidateTable, candidate);
    addAllToTransaction(transaction, approvalsToUpdate, approvalsToDelete, notesToUpdate);
    sendTransaction(transaction.build());
  }

  /**
   * Updates approvals and notes without modifying the candidate itself.
   *
   * @param dependentPeriod Period the candidate belongs to. Transaction fails if modified
   *     concurrently.
   * @param candidate Candidate the items belong to. Transaction fails if modified concurrently.
   * @param approvalsToUpdate Approval statuses to update
   * @param approvalsToDelete Approval statuses to delete
   * @param notesToUpdate Notes to update
   */
  public void updateCandidateItems(
      NviPeriodDao dependentPeriod,
      CandidateDao candidate,
      Collection<ApprovalStatusDao> approvalsToUpdate,
      Collection<ApprovalStatusDao> approvalsToDelete,
      Collection<NoteDao> notesToUpdate) {
    LOGGER.info("Updating approvals and notes for candidate {}", candidate.identifier());
    var transaction = TransactWriteItemsEnhancedRequest.builder();
    transaction.addConditionCheck(periodTable, requireExpectedPeriodRevision(dependentPeriod));
    transaction.addConditionCheck(candidateTable, requireExpectedCandidateRevision(candidate));
    addAllToTransaction(transaction, approvalsToUpdate, approvalsToDelete, notesToUpdate);
    sendTransaction(transaction.build());
  }

  /**
   * Fetches a candidate and all related items (approvals, notes) asynchronously.
   *
   * @param candidateId Candidate identifier
   * @return CompletableFuture containing all related database entries
   */
  public CompletableFuture<List<Dao>> getCandidateAggregateAsync(UUID candidateId) {
    LOGGER.info("Fetching candidate and related data for candidateId={}", candidateId);

    return executeAsync(getCandidateAggregateRequest(candidateId))
        .thenApply(QueryResponse::items)
        .thenApply(items -> items.stream().map(this::mapToDao).toList());
  }

  /**
   * Fetches a candidate by identifier.
   *
   * @param candidateIdentifier Candidate identifier
   * @return Optional containing the candidate, or empty if not found
   */
  public Optional<CandidateDao> findCandidateById(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate by identifier {}", candidateIdentifier);
    var candidateKey = createCandidateKey(candidateIdentifier);
    return Optional.ofNullable(candidateTable.getItem(getByKey(candidateKey)));
  }

  /**
   * Finds candidate identifier by publication ID via GSI. Re-fetch from primary table for strongly
   * consistent reads.
   */
  public Optional<UUID> findByPublicationId(URI publicationId) {
    LOGGER.info("Fetching candidate by publication id {}", publicationId);
    var publicationKey = createCandidateKeyByPublicationId(publicationId);
    var query = QueryEnhancedRequest.builder().queryConditional(keyEqualTo(publicationKey)).build();
    return publicationIdIndex.query(query).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .findFirst()
        .map(CandidateDao::identifier);
  }

  public void deleteNote(UUID candidateIdentifier, UUID noteIdentifier) {
    LOGGER.info("Deleting note: candidateId={}, noteId={}, ", candidateIdentifier, noteIdentifier);
    noteTable.deleteItem(createNoteKey(candidateIdentifier, noteIdentifier));
  }

  protected static Key createCandidateKey(UUID candidateIdentifier) {
    return Key.builder()
        .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .sortValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .build();
  }

  protected static Key createCandidateKeyByPublicationId(URI publicationId) {
    return Key.builder()
        .partitionValue(publicationId.toString())
        .sortValue(publicationId.toString())
        .build();
  }

  protected static Key createNoteKey(UUID candidateIdentifier, UUID noteIdentifier) {
    return Key.builder()
        .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .sortValue(NoteDao.createSortKey(noteIdentifier.toString()))
        .build();
  }

  private ConditionCheck<NviPeriodDao> requireExpectedPeriodRevision(NviPeriodDao period) {
    var periodKey = createPeriodKey(period.nviPeriod().publishingYear());
    var revisionCondition = requireExpectedRevision(period.revision());
    return ConditionCheck.builder().key(periodKey).conditionExpression(revisionCondition).build();
  }

  private ConditionCheck<CandidateDao> requireExpectedCandidateRevision(CandidateDao candidate) {
    var candidateKey = createCandidateKey(candidate.identifier());
    var revisionCondition = requireExpectedRevision(candidate.revision());
    return ConditionCheck.builder()
        .key(candidateKey)
        .conditionExpression(revisionCondition)
        .build();
  }

  private QueryRequest getCandidateAggregateRequest(UUID candidateId) {
    var candidateKey = CandidateDao.createPartitionKey(candidateId.toString());
    return QueryRequest.builder()
        .tableName(NVI_TABLE_NAME)
        .keyConditionExpression("#pk = :pk")
        .expressionAttributeNames(Map.of("#pk", HASH_KEY))
        .expressionAttributeValues(Map.of(":pk", AttributeValue.builder().s(candidateKey).build()))
        .consistentRead(true)
        .build();
  }

  private void addAllToTransaction(
      Builder transaction,
      Collection<ApprovalStatusDao> approvalsToUpdate,
      Collection<ApprovalStatusDao> approvalsToDelete,
      Collection<NoteDao> notesToUpdate) {

    LOGGER.info("Updating {} approvals", approvalsToUpdate.size());
    for (var updatedApproval : approvalsToUpdate) {
      addUpdatedItemWithVersion(transaction, approvalStatusTable, updatedApproval);
    }

    LOGGER.info("Deleting {} approvals", approvalsToDelete.size());
    for (var removedApproval : approvalsToDelete) {
      transaction.addDeleteItem(approvalStatusTable, removedApproval);
    }

    LOGGER.info("Updating {} approvals", notesToUpdate.size());
    for (var note : notesToUpdate) {
      addUpdatedItemWithVersion(transaction, noteTable, note);
    }
  }

  private Dao mapToDao(Map<String, AttributeValue> document) {
    var documentType = document.get("type").s();
    return switch (documentType) {
      case CandidateDao.TYPE -> candidateTable.tableSchema().mapToItem(document);
      case ApprovalStatusDao.TYPE -> approvalStatusTable.tableSchema().mapToItem(document);
      case NoteDao.TYPE -> noteTable.tableSchema().mapToItem(document);
      case NviPeriodDao.TYPE -> periodTable.tableSchema().mapToItem(document);
      default -> {
        LOGGER.error("Failed to map document type {}", documentType);
        throw new IllegalArgumentException("Failed to map document type");
      }
    };
  }

  protected static QueryConditional queryCandidateParts(UUID id, String type) {
    return sortBeginsWith(
        Key.builder()
            .partitionValue(CandidateDao.createPartitionKey(id.toString()))
            .sortValue(type)
            .build());
  }

  private static List<CandidateDao> filterOutReportedCandidates(Collection<CandidateDao> items) {
    return items.stream().filter(CandidateDao::isNotReported).toList();
  }

  private static <T> Stream<List<T>> getBatches(List<T> scanResult) {
    var count = scanResult.size();
    return IntStream.range(0, (count + BATCH_SIZE - 1) / BATCH_SIZE)
        .mapToObj(i -> scanResult.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, count)));
  }

  private static Map<String, AttributeValue> toAttributeMap(Map<String, String> startMarker) {
    return startMarker.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> AttributeValue.builder().s(e.getValue()).build()));
  }

  private static Map<String, String> toStringMap(Map<String, AttributeValue> lastEvaluatedKey) {
    return lastEvaluatedKey.entrySet().stream()
        .collect(toMap(Map.Entry::getKey, e -> e.getValue().s()));
  }

  private List<Dao> extractDatabaseEntries(ScanResponse response) {
    return response.items().stream()
        .map(value -> DynamoEntryWithRangeKey.parseAttributeValuesMap(value, Dao.class))
        .toList();
  }

  private List<WriteRequest> createWriteRequestsForBatchJob(List<Dao> refreshedEntries) {
    return refreshedEntries.stream()
        .map(Dao::toDynamoFormat)
        .map(this::mutateVersion)
        .map(this::toWriteRequest)
        .toList();
  }

  private Page<CandidateDao> queryYearIndex(
      String year, Integer pageSize, Map<String, String> startMarker) {
    return yearIndex.query(createQuery(year, pageSize, startMarker)).stream()
        .findFirst()
        .orElse(Page.builder(CandidateDao.class).items(emptyList()).build());
  }

  private QueryEnhancedRequest createQuery(
      String year, Integer pageSize, Map<String, String> startMarker) {
    var start = nonNull(startMarker) ? toAttributeMap(startMarker) : null;
    var limit = nonNull(pageSize) ? pageSize : DEFAULT_PAGE_SIZE;
    return QueryEnhancedRequest.builder()
        .queryConditional(keyEqualTo(Key.builder().partitionValue(year).build()))
        .limit(limit)
        .exclusiveStartKey(start)
        .build();
  }

  private Map<String, AttributeValue> mutateVersion(Map<String, AttributeValue> item) {
    var mutableMap = new HashMap<>(item);
    mutableMap.put(VERSION_FIELD, AttributeValue.builder().s(randomUUID().toString()).build());
    return mutableMap;
  }

  private boolean thereAreMorePagesToScan(Page<CandidateDao> page) {
    return nonNull(page) && hasElements(page.lastEvaluatedKey());
  }

  private boolean thereAreMorePagesToScan(ScanResponse scanResult) {
    return hasElements(scanResult.lastEvaluatedKey());
  }

  private BatchWriteItemRequest toBatchRequest(Collection<WriteRequest> writeRequests) {
    return BatchWriteItemRequest.builder()
        .requestItems(Map.of(NVI_TABLE_NAME, writeRequests))
        .build();
  }

  private WriteRequest toWriteRequest(Map<String, AttributeValue> dao) {
    return WriteRequest.builder().putRequest(toPutRequest(dao)).build();
  }

  private PutRequest toPutRequest(Map<String, AttributeValue> dao) {
    return PutRequest.builder().item(dao).build();
  }

  private ScanRequest createScanRequest(
      int pageSize, Map<String, String> startMarker, List<KeyField> types) {
    var start = nonNull(startMarker) ? toAttributeMap(startMarker) : null;
    return ScanRequest.builder()
        .tableName(NVI_TABLE_NAME)
        .expressionAttributeNames(Map.of("#PK", DatabaseConstants.SORT_KEY))
        .filterExpression(Dao.scanFilterExpressionForDataEntries(types))
        .expressionAttributeValues(Dao.scanFilterExpressionAttributeValues(types))
        .exclusiveStartKey(start)
        .limit(pageSize)
        .consistentRead(true)
        .build();
  }
}
