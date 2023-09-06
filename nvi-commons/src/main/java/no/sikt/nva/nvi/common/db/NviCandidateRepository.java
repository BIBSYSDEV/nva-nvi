package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromImmutableClass;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.sortBeginsWith;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;
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
    private final DynamoDbTable<CandidateUniquenessEntry> uniquenessTable;
    private final DynamoDbIndex<CandidateDao> publicationIdIndex;
    private final DynamoDbTable<ApprovalStatusDao> approvalStatusTable;

    public NviCandidateRepository(DynamoDbClient client) {
        super(client);
        this.candidateTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateDao.class));
        this.uniquenessTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(CandidateUniquenessEntry.class));
        this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
        this.approvalStatusTable = this.client.table(NVI_TABLE_NAME, fromImmutableClass(ApprovalStatusDao.class));
    }

    public Candidate create(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
        var identifier = UUID.randomUUID();
        var candidate = new CandidateDao(identifier, dbCandidate);
        var uniqueness = new CandidateUniquenessEntry(dbCandidate.publicationId().toString());
        var transactionBuilder = buildTransaction(approvalStatuses, candidate, identifier, uniqueness);

        this.client.transactWriteItems(transactionBuilder.build());
        var candidateObj = candidateTable.getItem(candidate);

        return new Candidate(candidateObj.identifier(), candidateObj.candidate(),
                             getApprovalStatuses(approvalStatusTable, candidateObj.identifier()));
    }

    public Candidate update(UUID identifier, DbCandidate dbCandidate,
                            List<DbApprovalStatus> approvalStatusList) {
        var candidate = CandidateDao.builder().identifier(identifier).candidate(dbCandidate).build();
        var approvalStatuses = approvalStatusList.stream().map(a -> new ApprovalStatusDao(identifier, a)).toList();
        var transaction = TransactWriteItemsEnhancedRequest.builder();
        transaction.addPutItem(candidateTable, candidate);
        approvalStatuses.forEach(approvalStatus -> transaction.addPutItem(approvalStatusTable, approvalStatus));
        // Maybe we need to remove the rows first, but preferably override
        client.transactWriteItems(transaction.build());
        return new Candidate(identifier, dbCandidate, approvalStatusList);
    }

    public Optional<Candidate> findById(UUID id) {
        var queryObj = new CandidateDao(id, DbCandidate.builder().build());
        var fetched = this.candidateTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(
            candidateDao -> new Candidate(id, candidateDao.candidate(), getApprovalStatuses(approvalStatusTable, id))
        );
    }

    public Candidate getById(UUID id) {
        var candidateDao = this.candidateTable.getItem(candidateKey(id));
        var approvalStatus = getApprovalStatuses(approvalStatusTable, id);

        return new Candidate(id, candidateDao.candidate(), approvalStatus);
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        var query = QueryConditional.keyEqualTo(Key.builder()
                                                    .partitionValue(publicationId.toString())
                                                    .sortValue(publicationId.toString())
                                                    .build());

        var result = this.publicationIdIndex.query(query);

        return result.stream()
                   .map(Page::items)
                   .flatMap(Collection::stream)
                   .map(
                       candidateDao -> new Candidate(candidateDao.identifier(), candidateDao.candidate(),
                                                     getApprovalStatuses(approvalStatusTable,
                                                                         candidateDao.identifier())))
                   .findFirst();
    }

    public Optional<DbApprovalStatus> findApprovalByIdAndInstitutionId(UUID identifier, URI uri) {
        var query = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(CandidateDao.pk0(identifier.toString()))
                .sortValue(ApprovalStatusDao.sk0(uri.toString())).build()
        );
        var result = approvalStatusTable.query(query);
        return result.items()
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .findFirst();
    }

    public void updateApprovalStatus(UUID identifier, DbApprovalStatus newStatus) {
        var insert = new ApprovalStatusDao(identifier, newStatus);
        approvalStatusTable.updateItem(insert);
    }

    private static Key candidateKey(UUID id) {
        return Key.builder()
                   .partitionValue(CandidateDao.pk0(id.toString()))
                   .sortValue(CandidateDao.pk0(id.toString()))
                   .build();
    }

    private static QueryConditional queryCandidateParts(UUID id, String type) {
        return sortBeginsWith(Key.builder()
                                  .partitionValue(CandidateDao.pk0(id.toString()))
                                  .sortValue(type)
                                  .build());
    }

    private static <T> TransactPutItemEnhancedRequest<T> insertTransaction(T insert, Class<T> clazz) {
        return TransactPutItemEnhancedRequest.builder(clazz)
                   .item(insert)
                   .conditionExpression(uniquePrimaryKeysExpression())
                   .build();
    }

    private List<DbApprovalStatus> getApprovalStatuses(DynamoDbTable<ApprovalStatusDao> approvalStatusTable,
                                                       UUID identifier) {
        return approvalStatusTable.query(
                queryCandidateParts(identifier, ApprovalStatusDao.TYPE))
                   .items()
                   .stream()
                   .map(ApprovalStatusDao::approvalStatus)
                   .toList();
    }

    private Builder buildTransaction(List<DbApprovalStatus> approvalStatuses, CandidateDao candidate, UUID identifier,
                                     CandidateUniquenessEntry uniqueness) {
        var transactionBuilder = TransactWriteItemsEnhancedRequest.builder();

        transactionBuilder.addPutItem(this.candidateTable, insertTransaction(candidate, CandidateDao.class));

        approvalStatuses
            .stream()
            .map(as -> new ApprovalStatusDao(identifier, as))
            .forEach(as -> transactionBuilder.addPutItem(this.approvalStatusTable,
                                                         insertTransaction(as, ApprovalStatusDao.class)));
        transactionBuilder.addPutItem(this.uniquenessTable,
                                      insertTransaction(uniqueness, CandidateUniquenessEntry.class));
        return transactionBuilder;
    }
}