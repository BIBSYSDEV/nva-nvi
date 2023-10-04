package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toMap;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromImmutableClass;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.sortBeginsWith;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.service.Candidate;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest.Builder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class CandidateRepository extends DynamoRepository {

    private final DynamoDbTable<CandidateDao> candidateTable;
    private final DynamoDbTable<CandidateUniquenessEntryDao> uniquenessTable;
    private final DynamoDbIndex<CandidateDao> publicationIdIndex;
    private final DynamoDbTable<ApprovalStatusDao> approvalStatusTable;
    private final DynamoDbTable<NoteDao> noteTable;

    public CandidateRepository(DynamoDbClient client) {
        super(client);
        this.candidateTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateDao.class));
        this.uniquenessTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateUniquenessEntryDao.class));
        this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
        this.approvalStatusTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(ApprovalStatusDao.class));
        this.noteTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(NoteDao.class));
    }

    public Candidate create(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
        return toCandidate(createDao(dbCandidate, approvalStatuses));
    }

    public CandidateDao createDao(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
        var identifier = randomUUID();
        var candidate = constructCandidate(identifier, dbCandidate);
        var uniqueness = new CandidateUniquenessEntryDao(dbCandidate.publicationId().toString());
        var transactionBuilder = buildTransaction(approvalStatuses, candidate, identifier, uniqueness);

        this.client.transactWriteItems(transactionBuilder.build());
        return candidateTable.getItem(candidate);
    }

    public void updateCandidateResettingApprovals(UUID identifier, CandidateDao candidateDao, List<DbApprovalStatus> approvals) {
        var approvalMap = approvals.stream()
                              .map(approval -> mapToApprovalStatusDao(identifier, approval))
                              .collect(toMap(approvalStatusDao -> approvalStatusDao.approvalStatus().institutionId(),
                                             Function.identity()));
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        addNoLongerValidApprovalsToTransaction(identifier, approvalMap, transaction);
        transaction.addPutItem(candidateTable, candidateDao);
        approvalMap.values().forEach(approvalStatus -> transaction.addPutItem(approvalStatusTable, approvalStatus));
        client.transactWriteItems(transaction.build());
    }

    public Candidate update(UUID identifier, DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatusList) {
        var candidate = constructCandidate(identifier, dbCandidate);
        var approvalStatuses = approvalStatusList.stream()
                                   .map(approval -> mapToApprovalStatusDao(identifier, approval))
                                   .toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);

        approvalStatuses.forEach(approvalStatus -> transaction.addPutItem(approvalStatusTable, approvalStatus));
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(dbCandidate)
                   .withApprovalStatuses(approvalStatusList)
                   .withNotes(getDbNotes(identifier))
                   .build();
    }

    public Optional<CandidateDao> findCandidateDaoById(UUID candidateIdentifier) {
        return Optional.ofNullable(candidateTable.getItem(Key.builder()
                                                              .partitionValue(CandidateDao.createPartitionKey(
                                                                  candidateIdentifier.toString()))
                                                              .sortValue(CandidateDao.createPartitionKey(
                                                                  candidateIdentifier.toString()))
                                                              .build()));
    }

    public Optional<Candidate> findCandidateById(UUID candidateIdentifier) {
        return Optional.of(CandidateDao.builder().identifier(candidateIdentifier).build())
                   .map(candidateTable::getItem)
                   .map(this::toCandidate);
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return findByPublicationIdDao(publicationId).map(this::toCandidate);
    }

    public Optional<CandidateDao> findByPublicationIdDao(URI publicationId) {
        return this.publicationIdIndex.query(findCandidateByPublicationIdQuery(publicationId))
                   .stream()
                   .map(Page::items)
                   .flatMap(Collection::stream)
                   .findFirst();
    }

    public ApprovalStatusDao updateApprovalStatusDao(UUID identifier, DbApprovalStatus newApproval) {
        return approvalStatusTable.updateItem(newApproval.toDao(identifier));
    }

    public NoteDao saveNote(UUID candidateIdentifier, DbNote dbNote) {
        NoteDao note = newNoteDao(candidateIdentifier, dbNote);
        noteTable.putItem(note);
        return note;
    }

    public Candidate updateCandidateRemovingApprovals(UUID identifier, DbCandidate dbCandidate,
                                                      List<DbApprovalStatus> approvals) {
        var candidate = constructCandidate(identifier, dbCandidate);
        var transaction = constructTransaction(approvals, candidate);
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(dbCandidate)
                   .withApprovalStatuses(approvals)
                   .withNotes(getDbNotes(identifier))
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

    public List<ApprovalStatusDao> fetchApprovals(UUID identifier) {
        return approvalStatusTable.query(constructApprovalsQuery(identifier)).items().stream().toList();
    }

    public List<DbNote> getDbNotes(UUID candidateIdentifier) {
        return noteTable.query(queryCandidateParts(candidateIdentifier, NoteDao.TYPE))
                   .items()
                   .stream()
                   .map(NoteDao::note)
                   .toList();
    }

    public List<NoteDao> getNotes(UUID candidateIdentifier) {
        return noteTable.query(queryCandidateParts(candidateIdentifier, NoteDao.TYPE)).items().stream().toList();
    }

    public void updateCandidateAndRemovingApprovals(UUID identifier, CandidateDao nonApplicableCandidate) {
        candidateTable.putItem(nonApplicableCandidate);
        var approvalStatuseDaos = getApprovalStatuses(identifier);
        if (!approvalStatuseDaos.isEmpty()) {
            var transactionBuilder = TransactWriteItemsEnhancedRequest.builder();
            approvalStatuseDaos.forEach(
                approvalStatusDao -> transactionBuilder.addDeleteItem(approvalStatusTable, approvalStatusDao));
            client.transactWriteItems(transactionBuilder.build());
        }
    }

    public void updateCandidateOnly(CandidateDao candidate) {
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        client.transactWriteItems(transaction.build());
    }

    private static ApprovalStatusDao mapToApprovalStatusDao(UUID identifier, DbApprovalStatus approval) {
        return ApprovalStatusDao.builder().identifier(identifier).approvalStatus(approval).build();
    }

    private static QueryConditional constructApprovalsQuery(UUID identifier) {
        return sortBeginsWith(Key.builder()
                                  .partitionValue(CandidateDao.createPartitionKey(identifier.toString()))
                                  .sortValue(ApprovalStatusDao.TYPE)
                                  .build());
    }

    private static CandidateDao constructCandidate(UUID identifier, DbCandidate dbCandidate) {
        return CandidateDao.builder()
                   .identifier(identifier)
                   .candidate(dbCandidate)
                   .version(randomUUID().toString())
                   .build();
    }

    private static NoteDao newNoteDao(UUID candidateIdentifier, DbNote dbNote) {
        return NoteDao.builder().identifier(candidateIdentifier).note(newDbNote(dbNote)).build();
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
        return QueryConditional.keyEqualTo(candidateByPublicationIdKey(publicationId));
    }

    private static Key candidateByPublicationIdKey(URI publicationId) {
        return Key.builder().partitionValue(publicationId.toString()).sortValue(publicationId.toString()).build();
    }

    private static Key noteKey(UUID candidateIdentifier, UUID noteIdentifier) {
        return Key.builder()
                   .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
                   .sortValue(NoteDao.createSortKey(noteIdentifier.toString()))
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

    private void addNoLongerValidApprovalsToTransaction(UUID identifier, Map<URI, ApprovalStatusDao> approvalMap,
                                                        Builder transaction) {
        var oldApprovals = new ArrayList<>(getApprovalStatuses(approvalStatusTable, identifier));
        if (oldApprovals.removeIf(old -> approvalMap.containsKey(old.institutionId()))) {
            oldApprovals.forEach(leftOver -> transaction.addDeleteItem(approvalStatusTable,
                                                                       mapToApprovalStatusDao(identifier, leftOver)));
        }
    }

    private Builder constructTransaction(List<DbApprovalStatus> approvals, CandidateDao candidate) {
        var approvalStatuses = approvals.stream().map(approval -> approval.toDao(candidate.identifier())).toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        approvalStatuses.forEach(approvalStatus -> transaction.addDeleteItem(approvalStatusTable, approvalStatus));
        return transaction;
    }

    private Candidate toCandidate(CandidateDao candidateDao) {
        return new Candidate.Builder().withIdentifier(candidateDao.identifier())
                   .withCandidate(candidateDao.candidate())
                   .withApprovalStatuses(getApprovalStatuses(approvalStatusTable, candidateDao.identifier()))
                   .withNotes(getDbNotes(candidateDao.identifier()))
                   .build();
    }

    private List<ApprovalStatusDao> getApprovalStatuses(UUID identifier) {
        return approvalStatusTable.query(queryCandidateParts(identifier, ApprovalStatusDao.TYPE))
                   .items()
                   .stream()
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