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
import no.sikt.nva.nvi.common.db.model.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.model.ApprovalStatusDao.ApprovalStatusData;
import no.sikt.nva.nvi.common.db.model.CandidateDao;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.CandidateUniquenessEntryDao;
import no.sikt.nva.nvi.common.db.model.NoteDao;
import no.sikt.nva.nvi.common.db.model.NoteDao.NoteData;
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

    public Candidate create(CandidateData candidateData, List<ApprovalStatusData> approvalStatuses) {
        var identifier = UUID.randomUUID();
        var approvalStatusesWithCandidateIdentifiers =
            injectCandidateIdentifier(approvalStatuses, identifier);
        var candidate = constructCandidate(identifier, candidateData);
        var uniqueness = new CandidateUniquenessEntryDao(candidateData.publicationId().toString());
        var transactionBuilder = buildTransaction(approvalStatusesWithCandidateIdentifiers, candidate,
                                                  identifier, uniqueness);

        this.client.transactWriteItems(transactionBuilder.build());
        var candidateDao = candidateTable.getItem(candidate);
        return toCandidate(candidateDao);
    }

    public Candidate update(UUID identifier, CandidateData candidateData, List<ApprovalStatusData> approvalStatusList) {
        var candidate = constructCandidate(identifier, candidateData);
        var approvalStatuses = approvalStatusList.stream().map(approval -> approval.toDao(identifier)).toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        approvalStatuses.forEach(approvalStatus -> transaction.addPutItem(approvalStatusTable, approvalStatus));
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(candidateData)
                   .withApprovalStatuses(approvalStatusList)
                   .withNotes(getNotes(identifier))
                   .build();
    }

    public Optional<Candidate> findCandidateById(UUID candidateIdentifier) {
        return Optional.of(new CandidateDao(candidateIdentifier, CandidateData.builder().build()))
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

    public Optional<ApprovalStatusData> findApprovalByIdAndInstitutionId(UUID identifier, URI uri) {
        return approvalStatusTable.query(findApprovalByCandidateIdAndInstitutionId(identifier, uri))
                   .items()
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .findFirst();
    }

    public ApprovalStatusData updateApprovalStatus(UUID identifier, ApprovalStatusData newApproval) {
        return approvalStatusTable.updateItem(newApproval.toDao(identifier)).approvalStatus();
    }

    public boolean exists(UUID identifier) {
        return findCandidateById(identifier).isPresent();
    }

    public void saveNote(UUID candidateIdentifier, NoteData noteData) {
        noteTable.putItem(newNoteDao(candidateIdentifier, noteData));
    }

    public Candidate updateCandidateRemovingApprovals(UUID identifier, CandidateData candidateData,
                                                      List<ApprovalStatusData> approvals) {
        var candidate = constructCandidate(identifier, candidateData);
        var transaction = constructTransaction(approvals, candidate);
        client.transactWriteItems(transaction.build());
        return new Candidate.Builder().withIdentifier(identifier)
                   .withCandidate(candidateData)
                   .withApprovalStatuses(approvals)
                   .withNotes(getNotes(identifier))
                   .build();
    }

    public void deleteNote(UUID candidateIdentifier, UUID noteIdentifier) {
        noteTable.deleteItem(noteKey(candidateIdentifier, noteIdentifier));
    }

    public NoteData getNoteById(UUID candidateIdentifier, UUID noteIdentifier) {
        return Optional.of(noteKey(candidateIdentifier, noteIdentifier))
                   .map(noteTable::getItem)
                   .map(NoteDao::note)
                   .orElseThrow(NoSuchElementException::new);
    }

    private static CandidateDao constructCandidate(UUID identifier, CandidateData candidateData) {
        return CandidateDao.builder().identifier(identifier).candidate(candidateData).build();
    }

    private static NoteDao newNoteDao(UUID candidateIdentifier, NoteData noteData) {
        return NoteDao.builder()
                   .identifier(candidateIdentifier)
                   .note(newDbNote(noteData))
                   .build();
    }

    private static NoteData newDbNote(NoteData noteData) {
        return NoteData.builder()
                   .noteId(UUID.randomUUID())
                   .text(noteData.text())
                   .user(noteData.user())
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

    private List<ApprovalStatusData> injectCandidateIdentifier(List<ApprovalStatusData> approvalStatuses,
                                                               UUID identifier) {
        return approvalStatuses.stream()
                   .map(approvalStatus -> injectCandidateIdentifier(identifier, approvalStatus)).toList();
    }

    private ApprovalStatusData injectCandidateIdentifier(UUID identifier, ApprovalStatusData approvalStatus) {
        return approvalStatus.copy().candidateIdentifier(identifier).build();
    }

    private Builder constructTransaction(List<ApprovalStatusData> approvals, CandidateDao candidate) {
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
                   .withNotes(getNotes(candidateDao.identifier()))
                   .build();
    }

    private List<NoteData> getNotes(UUID candidateIdentifier) {
        return noteTable.query(queryCandidateParts(candidateIdentifier, NoteDao.TYPE))
                   .items()
                   .stream()
                   .map(NoteDao::note)
                   .toList();
    }

    private List<ApprovalStatusData> getApprovalStatuses(DynamoDbTable<ApprovalStatusDao> approvalStatusTable,
                                                         UUID identifier) {
        return approvalStatusTable.query(queryCandidateParts(identifier, ApprovalStatusDao.TYPE))
                   .items()
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .toList();
    }

    private Builder buildTransaction(List<ApprovalStatusData> approvalStatuses, CandidateDao candidate, UUID identifier,
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