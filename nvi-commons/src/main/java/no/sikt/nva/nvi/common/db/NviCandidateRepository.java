package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromImmutableClass;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.sortBeginsWith;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusRow.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.CandidateRow.DbCandidate;
import no.sikt.nva.nvi.common.db.NoteRow.DbNote;
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

public class NviCandidateRepository extends DynamoRepository {

    private final DynamoDbTable<CandidateRow> candidateTable;
    private final DynamoDbTable<CandidateUniquenessEntryDao> uniquenessTable;
    private final DynamoDbIndex<CandidateRow> publicationIdIndex;
    private final DynamoDbTable<ApprovalStatusRow> approvalStatusTable;
    private final DynamoDbTable<NoteRow> noteTable;

    public NviCandidateRepository(DynamoDbClient client) {
        super(client);
        this.candidateTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateRow.class));
        this.uniquenessTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateUniquenessEntryDao.class));
        this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
        this.approvalStatusTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(ApprovalStatusRow.class));
        this.noteTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(NoteRow.class));
    }

    public Candidate create(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
        var identifier = UUID.randomUUID();
        var approvalStatusesWithCandidateIdentifiers =
            injectCandidateIdentifier(approvalStatuses, identifier);
        var candidate = constructCandidate(identifier, dbCandidate);
        var uniqueness = new CandidateUniquenessEntryDao(dbCandidate.publicationId().toString());
        var transactionBuilder = buildTransaction(approvalStatusesWithCandidateIdentifiers, candidate,
                                                  identifier, uniqueness);

        this.client.transactWriteItems(transactionBuilder.build());
        var candidateDao = candidateTable.getItem(candidate);
        return toCandidate(candidateDao);
    }

    public Candidate update(UUID identifier, DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatusList) {
        var candidate = constructCandidate(identifier, dbCandidate);
        var approvalStatuses = approvalStatusList.stream().map(approval -> approval.toDao(identifier)).toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        approvalStatuses.forEach(approvalStatus -> transaction.addPutItem(approvalStatusTable, approvalStatus));
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(dbCandidate)
                   .withApprovalStatuses(approvalStatusList)
                   .withNotes(getNotes(identifier))
                   .build();
    }

    public Optional<Candidate> findCandidateById(UUID candidateIdentifier) {
        return Optional.of(new CandidateRow(candidateIdentifier, DbCandidate.builder().build()))
                   .map(candidateTable::getItem)
                   .map(this::toCandidate);
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
                   .map(ApprovalStatusRow::approvalStatus)
                   .findFirst();
    }

    public DbApprovalStatus updateApprovalStatus(UUID identifier, DbApprovalStatus newApproval) {
        return approvalStatusTable.updateItem(newApproval.toDao(identifier)).approvalStatus();
    }

    public boolean exists(UUID identifier) {
        return findCandidateById(identifier).isPresent();
    }

    public void saveNote(UUID candidateIdentifier, DbNote dbNote) {
        noteTable.putItem(newNoteDao(candidateIdentifier, dbNote));
    }

    public Candidate updateCandidateRemovingApprovals(UUID identifier, DbCandidate dbCandidate,
                                                      List<DbApprovalStatus> approvals) {
        var candidate = constructCandidate(identifier, dbCandidate);
        var transaction = constructTransaction(approvals, candidate);
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(dbCandidate)
                   .withApprovalStatuses(approvals)
                   .withNotes(getNotes(identifier))
                   .build();
    }

    public void deleteNote(UUID candidateIdentifier, UUID noteIdentifier) {
        noteTable.deleteItem(noteKey(candidateIdentifier, noteIdentifier));
    }

    public DbNote getNoteById(UUID candidateIdentifier, UUID noteIdentifier) {
        return Optional.of(noteKey(candidateIdentifier, noteIdentifier))
                   .map(noteTable::getItem)
                   .map(NoteRow::note)
                   .orElseThrow(NoSuchElementException::new);
    }

    private static CandidateRow constructCandidate(UUID identifier, DbCandidate dbCandidate) {
        return CandidateRow.builder().identifier(identifier).candidate(dbCandidate).build();
    }

    private static NoteRow newNoteDao(UUID candidateIdentifier, DbNote dbNote) {
        return NoteRow.builder()
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
                   .partitionValue(CandidateRow.createPartitionKey(identifier.toString()))
                   .sortValue(ApprovalStatusRow.createSortKey(uri.toString()))
                   .build();
    }

    private static Key noteKey(UUID candidateIdentifier, UUID noteIdentifier) {
        return Key.builder()
                   .partitionValue(CandidateRow.createPartitionKey(candidateIdentifier.toString()))
                   .sortValue(NoteRow.createSortKey(noteIdentifier.toString()))
                   .build();
    }

    private static QueryConditional queryCandidateParts(UUID id, String type) {
        return sortBeginsWith(
            Key.builder().partitionValue(CandidateRow.createPartitionKey(id.toString())).sortValue(type).build());
    }

    private static <T> TransactPutItemEnhancedRequest<T> insertTransaction(T insert, Class<T> clazz) {
        return TransactPutItemEnhancedRequest.builder(clazz)
                   .item(insert)
                   .conditionExpression(uniquePrimaryKeysExpression())
                   .build();
    }

    private List<DbApprovalStatus> injectCandidateIdentifier(List<DbApprovalStatus> approvalStatuses, UUID identifier) {
        return approvalStatuses.stream()
                   .map(approvalStatus -> injectCandidateIdentifier(identifier, approvalStatus)).toList();
    }

    private DbApprovalStatus injectCandidateIdentifier(UUID identifier, DbApprovalStatus approvalStatus) {
        return approvalStatus.copy().candidateIdentifier(identifier).build();
    }

    private Builder constructTransaction(List<DbApprovalStatus> approvals, CandidateRow candidate) {
        var approvalStatuses = approvals.stream().map(approval -> approval.toDao(candidate.identifier())).toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        approvalStatuses.forEach(approvalStatus -> transaction.addDeleteItem(approvalStatusTable, approvalStatus));
        return transaction;
    }

    private Candidate toCandidate(CandidateRow candidateRow) {
        return new Candidate.Builder().withIdentifier(candidateRow.identifier())
                   .withCandidate(candidateRow.candidate())
                   .withApprovalStatuses(getApprovalStatuses(approvalStatusTable, candidateRow.identifier()))
                   .withNotes(getNotes(candidateRow.identifier()))
                   .build();
    }

    private List<DbNote> getNotes(UUID candidateIdentifier) {
        return noteTable.query(queryCandidateParts(candidateIdentifier, NoteRow.TYPE))
                   .items()
                   .stream()
                   .map(NoteRow::note)
                   .toList();
    }

    private List<DbApprovalStatus> getApprovalStatuses(DynamoDbTable<ApprovalStatusRow> approvalStatusTable,
                                                       UUID identifier) {
        return approvalStatusTable.query(queryCandidateParts(identifier, ApprovalStatusRow.TYPE))
                   .items()
                   .stream()
                   .map(ApprovalStatusRow::approvalStatus)
                   .toList();
    }

    private Builder buildTransaction(List<DbApprovalStatus> approvalStatuses, CandidateRow candidate, UUID identifier,
                                     CandidateUniquenessEntryDao uniqueness) {
        var transactionBuilder = TransactWriteItemsEnhancedRequest.builder();

        transactionBuilder.addPutItem(this.candidateTable, insertTransaction(candidate, CandidateRow.class));

        approvalStatuses.stream()
            .map(approval -> approval.toDao(identifier))
            .forEach(approval -> transactionBuilder.addPutItem(this.approvalStatusTable,
                                                               insertTransaction(approval, ApprovalStatusRow.class)));
        transactionBuilder.addPutItem(this.uniquenessTable,
                                      insertTransaction(uniqueness, CandidateUniquenessEntryDao.class));
        return transactionBuilder;
    }
}