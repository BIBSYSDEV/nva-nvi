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
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.DatabaseConstants;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
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

  public CandidateDao create(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
    return create(dbCandidate, approvalStatuses, dbCandidate.getPublicationDate().year());
  }

  public CandidateDao create(
      DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses, String year) {
    LOGGER.info(
        "Creating candidate for publication {} for year {} and with approvals {}",
        dbCandidate.publicationId(),
        year,
        approvalStatuses);
    var identifier = randomUUID();
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

  public void updateCandidate(CandidateDao candidate) {
    LOGGER.info("Updating candidate {}", candidate.identifier());
    var transaction = TransactWriteItemsEnhancedRequest.builder();
    transaction.addPutItem(candidateTable, candidate);
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
    transaction.addPutItem(candidateTable, candidateDao);

    for (var updatedApproval : approvalsToUpdate) {
      transaction.addPutItem(approvalStatusTable, updatedApproval);
    }

    for (var otherApproval : approvalsToDelete) {
      transaction.addDeleteItem(approvalStatusTable, otherApproval);
    }

    sendTransaction(transaction.build());
  }

  private void sendTransaction(TransactWriteItemsEnhancedRequest request) {
    try {
      client.transactWriteItems(request);
    } catch (TransactionCanceledException transactionCanceledException) {
      throw TransactionException.from(transactionCanceledException, request);
    }
  }

  public Optional<CandidateDao> findCandidateById(UUID candidateIdentifier) {
    return Optional.ofNullable(
        candidateTable.getItem(
            Key.builder()
                .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                .sortValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                .build()));
  }

  public Optional<CandidateDao> findByPublicationId(URI publicationId) {
    return this.publicationIdIndex.query(findCandidateByPublicationIdQuery(publicationId)).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .findFirst();
  }

  public ApprovalStatusDao updateApprovalStatusDao(
      CandidateDao candidate, ApprovalStatusDao approval) {
    LOGGER.info(
        "Updating approval: candidateId={}, approval={}",
        approval.identifier(),
        approval.approvalStatus());
    var transaction = TransactWriteItemsEnhancedRequest.builder();
    transaction.addUpdateItem(approvalStatusTable, approval);
    transaction.addConditionCheck(candidateTable, requireExpectedCandidateRevision(candidate));

    try {
      client.transactWriteItems(transaction.build());
      LOGGER.info("Successfully updated approval for candidateId={}", approval.identifier());
      return approval;

    } catch (TransactionCanceledException e) {
      LOGGER.error("Failed to update approval: approval={}", approval);
      handleTransactionFailure(e, approval);
      throw TransactionException.from(e, transaction.build());
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

  public NoteDao saveNote(UUID candidateIdentifier, DbNote dbNote) {
    LOGGER.info("Saving note: candidateId={}, note={}", candidateIdentifier, dbNote);
    var note = newNoteDao(candidateIdentifier, dbNote);
    noteTable.putItem(note);
    return note;
  }

  public void deleteNote(UUID candidateIdentifier, UUID noteIdentifier) {
    LOGGER.info("Deleting note: candidateId={}, noteId={}, ", candidateIdentifier, noteIdentifier);
    noteTable.deleteItem(noteKey(candidateIdentifier, noteIdentifier));
  }

  public List<ApprovalStatusDao> fetchApprovals(UUID identifier) {
    return approvalStatusTable.query(constructApprovalsQuery(identifier)).items().stream().toList();
  }

  public List<NoteDao> getNotes(UUID candidateIdentifier) {
    return noteTable.query(queryCandidateParts(candidateIdentifier, NoteDao.TYPE)).items().stream()
        .toList();
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

  private static QueryConditional constructApprovalsQuery(UUID identifier) {
    return sortBeginsWith(
        Key.builder()
            .partitionValue(CandidateDao.createPartitionKey(identifier.toString()))
            .sortValue(ApprovalStatusDao.TYPE)
            .build());
  }

  private static NoteDao newNoteDao(UUID candidateIdentifier, DbNote dbNote) {
    return NoteDao.builder()
        .identifier(candidateIdentifier)
        .version(randomUUID().toString())
        .note(newDbNote(dbNote))
        .build();
  }

  private static DbNote newDbNote(DbNote dbNote) {
    return DbNote.builder()
        .noteId(randomUUID())
        .text(dbNote.text())
        .user(dbNote.user())
        .createdDate(Instant.now())
        .build();
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
