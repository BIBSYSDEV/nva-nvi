package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromImmutableClass;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.sortBeginsWith;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.model.ListingResult;
import org.slf4j.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest.Builder;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class NviCandidateRepository extends DynamoRepository {

    public static final int BATCH_SIZE = 25;
    private final DynamoDbTable<CandidateDao> candidateTable;
    private final DynamoDbTable<CandidateUniquenessEntryDao> uniquenessTable;
    private final DynamoDbIndex<CandidateDao> publicationIdIndex;
    private final DynamoDbTable<ApprovalStatusDao> approvalStatusTable;
    private final DynamoDbTable<NoteDao> noteTable;

    public NviCandidateRepository(DynamoDbClient client) {
        super(client);
        this.candidateTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateDao.class));
        this.uniquenessTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateUniquenessEntryDao.class));
        this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
        this.approvalStatusTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(ApprovalStatusDao.class));
        this.noteTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(NoteDao.class));
    }

    public <T> ListingResult<CandidateDao> refresh(int pageSize, Map<String,
                                                      String> startMarker) {
        var page = candidateTable.scan(createScanRequest(pageSize, startMarker))
                             .stream()
                             .limit(1)
                             .findFirst();

        var batchResults = Optional.of(getBatches(page.get().items(), BATCH_SIZE)
                            .map(this::toBatchRequest)
                            .map(client::batchWriteItem)
                            .toList())
                       .orElse(List.of());

        return new ListingResult<>(thereAreMorePagesToScan(page.get()), page.get().lastEvaluatedKey(),
                                   batchResults.size(),
                                   batchResults.stream().mapToInt(a -> a.unprocessedPutItemsForTable(candidateTable).size()).sum(),
                                   batchResults.stream().mapToInt(a -> a.unprocessedDeleteItemsForTable(candidateTable).size()).sum());
    }

    private int countSuccessfulWrites(BatchWriteResult batchWriteResult) {
        return batchWriteResult.unprocessedPutItemsForTable(candidateTable).size();
    }

    private boolean thereAreMorePagesToScan(Page<CandidateDao> scanResult) {
        return nonNull(scanResult.lastEvaluatedKey()) && !scanResult.lastEvaluatedKey().isEmpty();
    }

    private static <T> Stream<List<T>> getBatches(List<T> scanResult, int batchSize) {
        var count = scanResult.size();
        return IntStream.range(0, (count + batchSize - 1) / batchSize)
                   .mapToObj(i -> scanResult.subList(i * batchSize, Math.min((i + 1) * batchSize,
                                                                                     count)));
    }

    private BatchWriteItemEnhancedRequest toBatchRequest(List<CandidateDao> results) {
        return BatchWriteItemEnhancedRequest.builder()
                   .writeBatches(toWriteBatches(results))
                   .build();
    }

    private Collection<WriteBatch> toWriteBatches(List<CandidateDao> results) {
        return results.stream().map(this::toWriteBatch).toList();
    }

    private WriteBatch toWriteBatch(CandidateDao dao) {
        return WriteBatch.builder(CandidateDao.class)
                   .mappedTableResource(candidateTable)
                   .addPutItem(dao)
                   .build();
    }

    private ScanEnhancedRequest createScanRequest(int pageSize, Map<String, String> startMarker) {
        var start = startMarker!= null ? startMarker.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                                                      e -> AttributeValue.builder().s(e.getValue()).build())) : null;
        return ScanEnhancedRequest.builder()
                   .filterExpression(filterExpressionToScanCandidates())
                   .exclusiveStartKey(start)
                   .limit(pageSize)
                   .build();
    }

    private static Expression filterExpressionToScanCandidates() {
        return Expression.builder()
                   .expression("begins_with (#PK, :CANDIDATE) and begins_with (#RK, :CANDIDATE)")
                   .expressionNames(Map.of("#PK", "PrimaryKeyHashKey",
                                           "#RK", "PrimaryKeyRangeKey"))
                   .expressionValues(Map.of(":CANDIDATE", AttributeValue.fromS("CANDIDATE")))
                   .build();
    }

    public Candidate create(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
        var identifier = UUID.randomUUID();
        var candidate = new CandidateDao(identifier, dbCandidate);
        var uniqueness = new CandidateUniquenessEntryDao(dbCandidate.publicationId().toString());
        var transactionBuilder = buildTransaction(approvalStatuses, candidate, identifier, uniqueness);

        this.client.transactWriteItems(transactionBuilder.build());
        var candidateDao = candidateTable.getItem(candidate);
        return toCandidate(candidateDao);
    }

    public Candidate update(UUID identifier, DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatusList) {
        var candidate = CandidateDao.builder().identifier(identifier).candidate(dbCandidate).build();
        var approvalStatuses = approvalStatusList.stream().map(approval -> approval.toDao(identifier)).toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        approvalStatuses.forEach(approvalStatus -> transaction.addPutItem(approvalStatusTable, approvalStatus));
        // Maybe we need to remove the rows first, but preferably override
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(dbCandidate)
                   .withApprovalStatuses(approvalStatusList)
                   .withNotes(getNotes(identifier))
                   .build();
    }

    public Optional<Candidate> findCandidateById(UUID candidateIdentifier) {
        return Optional.of(new CandidateDao(candidateIdentifier, DbCandidate.builder().build()))
                   .map(candidateTable::getItem)
                   .map(this::toCandidate);
    }

    public Candidate getCandidateById(UUID candidateIdentifier) {
        return toCandidate(this.candidateTable.getItem(candidateKey(candidateIdentifier)));
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return this.publicationIdIndex.query(findCandidateByPublicationIdQuery(publicationId)).stream()
                   .map(Page::items)
                   .flatMap(Collection::stream)
                   .map(this::toCandidate)
                   .findFirst();
    }

    public Optional<DbApprovalStatus> findApprovalByIdAndInstitutionId(UUID identifier, URI uri) {
        return approvalStatusTable.query(findApprovalByCandidateIdAndInstitutionId(identifier, uri))
                   .items()
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .findFirst();
    }

    public void updateApprovalStatus(UUID identifier, DbApprovalStatus newApproval) {
        approvalStatusTable.updateItem(newApproval.toDao(identifier));
    }

    public boolean exists(UUID identifier) {
        return findCandidateById(identifier).isPresent();
    }

    public void saveNote(UUID candidateIdentifier, DbNote dbNote) {
        noteTable.putItem(newNoteDao(candidateIdentifier, dbNote));
    }

    private static NoteDao newNoteDao(UUID candidateIdentifier, DbNote dbNote) {
        return NoteDao.builder()
                   .identifier(candidateIdentifier)
                   .note(newDbNote(dbNote))
                   .build();
    }

    private static DbNote newDbNote(DbNote dbNote) {
        return DbNote.builder()
                   .noteId(UUID.randomUUID())
                   .text(dbNote.text())
                   .user(dbNote.user())
                   .createdDate(Instant.now())
                   .build();
    }

    public void deleteNote(UUID candidateIdentifier, UUID noteIdentifier) {
        noteTable.deleteItem(noteKey(candidateIdentifier, noteIdentifier));
    }

    public DbNote getNoteById(UUID candidateIdentifier, UUID noteIdentifier) {
        return Optional.of(noteKey(candidateIdentifier, noteIdentifier))
                   .map(noteTable::getItem)
                   .map(NoteDao::note)
                   .orElseThrow(NoSuchElementException::new);
    }

    private static QueryConditional findCandidateByPublicationIdQuery(URI publicationId) {
        return QueryConditional.keyEqualTo(candidateByPublicationIdKey(publicationId));
    }

    private static Key candidateByPublicationIdKey(URI publicationId) {
        return Key.builder().partitionValue(publicationId.toString()).sortValue(publicationId.toString()).build();
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

    private static Key noteKey(UUID candidateIdentifier, UUID noteIdentifier) {
        return Key.builder()
                   .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                   .sortValue(NoteDao.createSortKey(noteIdentifier.toString()))
                   .build();
    }

    private static Key candidateKey(UUID candidateIdentifier) {
        return Key.builder()
                   .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                   .sortValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                   .build();
    }

    private static QueryConditional queryCandidateParts(UUID id, String type) {
        return sortBeginsWith(
            Key.builder().partitionValue(CandidateDao.createPartitionKey(id.toString())).sortValue(type).build());
    }

    private static <T> TransactPutItemEnhancedRequest<T> insertTransaction(T insert, Class<T> clazz) {
        return TransactPutItemEnhancedRequest.builder(clazz)
                   .item(insert)
                   .conditionExpression(uniquePrimaryKeysExpression())
                   .build();
    }

    private Candidate toCandidate(CandidateDao candidateDao) {
        return new Candidate.Builder().withIdentifier(candidateDao.identifier())
                   .withCandidate(candidateDao.candidate())
                   .withApprovalStatuses(getApprovalStatuses(approvalStatusTable, candidateDao.identifier()))
                   .withNotes(getNotes(candidateDao.identifier()))
                   .build();
    }

    private List<DbNote> getNotes(UUID candidateIdentifier) {
        return noteTable.query(queryCandidateParts(candidateIdentifier, NoteDao.TYPE))
                   .items()
                   .stream()
                   .map(NoteDao::note)
                   .toList();
    }

    private List<DbApprovalStatus> getApprovalStatuses(DynamoDbTable<ApprovalStatusDao> approvalStatusTable,
                                                       UUID identifier) {
        return approvalStatusTable.query(queryCandidateParts(identifier, ApprovalStatusDao.TYPE))
                   .items()
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .toList();
    }

    private Builder buildTransaction(List<DbApprovalStatus> approvalStatuses, CandidateDao candidate, UUID identifier,
                                     CandidateUniquenessEntryDao uniqueness) {
        var transactionBuilder = TransactWriteItemsEnhancedRequest.builder();

        transactionBuilder.addPutItem(this.candidateTable, insertTransaction(candidate, CandidateDao.class));

        approvalStatuses.stream()
            .map(approval -> approval.toDao(identifier))
            .forEach(approval -> transactionBuilder.addPutItem(this.approvalStatusTable,
                                                               insertTransaction(approval, ApprovalStatusDao.class)));
        transactionBuilder.addPutItem(this.uniquenessTable,
                                      insertTransaction(uniqueness, CandidateUniquenessEntryDao.class));
        return transactionBuilder;
    }
}