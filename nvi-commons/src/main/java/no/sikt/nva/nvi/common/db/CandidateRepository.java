package no.sikt.nva.nvi.common.db;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR;
import static no.sikt.nva.nvi.common.DatabaseConstants.VERSION_FIELD;
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
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.model.ListingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
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
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
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

  public ListingResult<CandidateDao> fetchCandidatesByYear(
      String year,
      boolean includeReportedCandidates,
      Integer pageSize,
      Map<String, String> startMarker) {
    var page = queryYearIndex(year, pageSize, startMarker);
    return new ListingResult<>(
        thereAreMorePagesToScan(page),
        nonNull(page.lastEvaluatedKey()) ? toStringMap(page.lastEvaluatedKey()) : emptyMap(),
        page.items().size(),
        includeReportedCandidates ? page.items() : filterOutReportedCandidates(page));
  }

  // FIXME: make it void
  public CandidateDao create(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
    return create(dbCandidate, approvalStatuses, dbCandidate.getPublicationDate().year());
  }

  // FIXME: make it void
  public CandidateDao create(
      DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses, String year) {
    LOGGER.info(
        "Creating candidate for publication {} for year {} and with approvals {}",
        dbCandidate.publicationId(),
        year,
        approvalStatuses);
    var identifier = randomUUID(); // FIXME: Handle this in the service
    var candidate =
        CandidateDao.builder()
            .identifier(identifier)
            .candidate(dbCandidate)
            .periodYear(year)
            .version(randomUUID().toString())
            .build();
    var uniqueness = new CandidateUniquenessEntryDao(dbCandidate.publicationId().toString());
    var transactionBuilder = buildTransaction(approvalStatuses, candidate, identifier, uniqueness);

    sendTransaction(transactionBuilder.build());
    return candidateTable.getItem(candidate);
  }

  public void create(CandidateDao candidate, Collection<ApprovalStatusDao> approvals) {
    LOGGER.info("Creating new candidate with identifier={}", candidate.identifier());
    var transaction = TransactWriteItemsEnhancedRequest.builder();

    var publicationId = candidate.candidate().publicationId();
    var uniquenessEntry = new CandidateUniquenessEntryDao(publicationId.toString());
    transaction.addPutItem(
        uniquenessTable, insertTransaction(uniquenessEntry, CandidateUniquenessEntryDao.class));

    transaction.addPutItem(candidateTable, mutateDaoVersion(candidate));

    for (var approval : approvals) {
      transaction.addPutItem(approvalStatusTable, mutateDaoVersion(approval));
    }

    sendTransaction(transaction.build());
  }

  public void updateCandidateItems(
      CandidateDao candidate, Collection<ApprovalStatusDao> approvals, Collection<NoteDao> notes) {
    LOGGER.info("Updating approvals and notes for candidate {}", candidate.identifier());
    var transaction = TransactWriteItemsEnhancedRequest.builder();
    transaction.addConditionCheck(candidateTable, requireExpectedCandidateRevision(candidate));

    for (var approval : approvals) {
      var updatedApproval = mutateDaoVersion(approval);
      transaction.addPutItem(approvalStatusTable, updatedApproval);
    }

    for (var note : notes) {
      var updatedNote = mutateDaoVersion(note);
      transaction.addPutItem(noteTable, updatedNote);
    }

    sendTransaction(transaction.build());
  }

  public void updateCandidateAndApprovals(
      CandidateDao candidateDao,
      Collection<ApprovalStatusDao> approvalsToUpdate,
      Collection<ApprovalStatusDao> approvalsToDelete) {
    LOGGER.info("Updating candidate {} and resetting approvals", candidateDao.identifier());
    LOGGER.info("Updating approvals in: {}", approvalsToUpdate);
    LOGGER.info("Deleting approvals in: {}", approvalsToDelete);
    var transaction = TransactWriteItemsEnhancedRequest.builder();
    var updatedCandidate = mutateDaoVersion(candidateDao);
    transaction.addPutItem(candidateTable, updatedCandidate);

    for (var approval : approvalsToUpdate) {
      var updatedApproval = mutateDaoVersion(approval);
      transaction.addPutItem(approvalStatusTable, updatedApproval);
    }

    for (var otherApproval : approvalsToDelete) {
      transaction.addDeleteItem(approvalStatusTable, otherApproval);
    }

    sendTransaction(transaction.build());
  }

  @Deprecated // TODO: Remove this
  public Optional<CandidateDao> findCandidateById(UUID candidateIdentifier) {
    LOGGER.info("Fetching candidate by identifier {}", candidateIdentifier);
    return Optional.ofNullable(
        candidateTable.getItem(
            Key.builder()
                .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                .sortValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                .build()));
  }

  public Optional<CandidateDao> findByPublicationId(URI publicationId) {
    LOGGER.info("Fetching candidate by publication id {}", publicationId);
    return this.publicationIdIndex.query(findCandidateByPublicationIdQuery(publicationId)).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .findFirst();
  }

  public void deleteNote(UUID candidateIdentifier, UUID noteIdentifier) {
    LOGGER.info("Deleting note: candidateId={}, noteId={}, ", candidateIdentifier, noteIdentifier);
    noteTable.deleteItem(noteKey(candidateIdentifier, noteIdentifier));
  }

  private void sendTransaction(TransactWriteItemsEnhancedRequest request) {
    try {
      client.transactWriteItems(request);
    } catch (TransactionCanceledException transactionCanceledException) {
      throw TransactionException.from(transactionCanceledException, request);
    }
  }

  private ConditionCheck<CandidateDao> requireExpectedCandidateRevision(CandidateDao candidate) {
    var candidateKey = createCandidateKey(candidate.identifier());
    var revisionCondition = createRevisionCondition(candidate.revision());
    return ConditionCheck.builder()
        .key(candidateKey)
        .conditionExpression(revisionCondition)
        .build();
  }

  private void handleTransactionFailure(
      TransactionCanceledException e, ApprovalStatusDao approval) {
    LOGGER.error("Transaction cancelled for candidate {}", approval.identifier(), e);
    for (var reason : e.cancellationReasons()) {
      LOGGER.error("Cancellation reason: {}", reason);
    }
  }

  private Key createCandidateKey(UUID candidateIdentifier) {
    return Key.builder()
        .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .sortValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .build();
  }

  private Expression createRevisionCondition(Long expectedCandidateRevision) {
    if (isNull(expectedCandidateRevision)) {
      return Expression.builder().expression("attribute_not_exists(revision)").build();
    }

    return Expression.builder()
        .expression("revision = :expectedCandidateRevision")
        .expressionValues(
            Map.of(
                ":expectedCandidateRevision",
                AttributeValue.builder().n(expectedCandidateRevision.toString()).build()))
        .build();
  }

  public CompletableFuture<List<Dao>> getCandidateAggregateAsync(UUID candidateId) {
    LOGGER.info("Fetching candidate and related data for candidateId={}", candidateId);

    return executeAsync(getCandidateAggregateRequest(candidateId))
        .thenApply(QueryResponse::items)
        .thenApply(items -> items.stream().map(this::mapToDao).toList());
  }

  private QueryRequest getCandidateAggregateRequest(UUID candidateId) {
    var candidatePartitionKey = CandidateDao.createPartitionKey(candidateId.toString());
    return queryByPartitionKey(candidatePartitionKey);
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

  protected static Key noteKey(UUID candidateIdentifier, UUID noteIdentifier) {
    return Key.builder()
        .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .sortValue(NoteDao.createSortKey(noteIdentifier.toString()))
        .build();
  }

  protected static QueryConditional queryCandidateParts(UUID id, String type) {
    return sortBeginsWith(
        Key.builder()
            .partitionValue(CandidateDao.createPartitionKey(id.toString()))
            .sortValue(type)
            .build());
  }

  private static List<CandidateDao> filterOutReportedCandidates(Page<CandidateDao> page) {
    return page.items().stream().filter(CandidateDao::isNotReported).toList();
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

  private static QueryConditional findCandidateByPublicationIdQuery(URI publicationId) {
    return keyEqualTo(candidateByPublicationIdKey(publicationId));
  }

  private static Key candidateByPublicationIdKey(URI publicationId) {
    return Key.builder()
        .partitionValue(publicationId.toString())
        .sortValue(publicationId.toString())
        .build();
  }

  // FIXME: Maybe we need to use this everywhere?
  private static <T> TransactPutItemEnhancedRequest<T> insertTransaction(T insert, Class<T> clazz) {
    return TransactPutItemEnhancedRequest.builder(clazz)
        .item(insert)
        .conditionExpression(uniquePrimaryKeysExpression())
        .build();
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
    return this.yearIndex.query(createQuery(year, pageSize, startMarker)).stream()
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

  private CandidateDao mutateDaoVersion(CandidateDao originalDao) {
    return originalDao.copy().version(randomUUID().toString()).build();
  }

  private ApprovalStatusDao mutateDaoVersion(ApprovalStatusDao originalDao) {
    return originalDao.copy().version(randomUUID().toString()).build();
  }

  private NoteDao mutateDaoVersion(NoteDao originalDao) {
    return originalDao.copy().version(randomUUID().toString()).build();
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
        .build();
  }

  private Builder buildTransaction(
      List<DbApprovalStatus> approvalStatuses,
      CandidateDao candidate,
      UUID identifier,
      CandidateUniquenessEntryDao uniqueness) {
    var transactionBuilder = TransactWriteItemsEnhancedRequest.builder();

    transactionBuilder.addPutItem(
        this.candidateTable, insertTransaction(candidate, CandidateDao.class));

    approvalStatuses.stream()
        .map(approval -> approval.toDao(identifier))
        .forEach(
            approval ->
                transactionBuilder.addPutItem(
                    this.approvalStatusTable,
                    insertTransaction(approval, ApprovalStatusDao.class)));
    transactionBuilder.addPutItem(
        this.uniquenessTable, insertTransaction(uniqueness, CandidateUniquenessEntryDao.class));
    return transactionBuilder;
  }
}
