package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.ApprovalStatusWithIdentifier;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviCandidateRepository extends DynamoRepository {

    private final DynamoDbTable<CandidateDao> candidateTable;
    private final DynamoDbTable<CandidateUniquenessEntry> uniquenessTable;
    private final DynamoDbIndex<CandidateDao> publicationIdIndex;
    private final DynamoDbTable<ApprovalStatusDao> approvalStatusTable;

    public NviCandidateRepository(DynamoDbClient client) {
        super(client);
        this.candidateTable = this.client.table(NVI_TABLE_NAME, CandidateDao.TABLE_SCHEMA);
        this.uniquenessTable = this.client.table(NVI_TABLE_NAME, CandidateUniquenessEntry.TABLE_SCHEMA);
        this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
        this.approvalStatusTable = this.client.table(NVI_TABLE_NAME, ApprovalStatusDao.TABLE_SCHEMA);
    }

    public CandidateWithIdentifier create(Candidate candidate) {
        var uuid = UUID.randomUUID();
        var insert = new CandidateDao(uuid, candidate);
        var uniqueness = new CandidateUniquenessEntry(candidate.publicationId().toString());
        var transactionBuilder = TransactWriteItemsEnhancedRequest.builder();

        // CREATE CANDIDATE
        transactionBuilder.addPutItem(this.candidateTable, insertTransaction(insert, CandidateDao.class));
        // CREATE APPROVAL_STATUSES
        candidate.approvalStatuses().stream()
            .map(ap -> new ApprovalStatusDao(uuid, ap))
            .forEach(approvalStatusDao ->
                         transactionBuilder.addPutItem(this.approvalStatusTable,
                                                       insertTransaction(approvalStatusDao, ApprovalStatusDao.class)));
        // CREATE UNIQUENESS CANDIDATES
        transactionBuilder.addPutItem(this.uniquenessTable,
                                      insertTransaction(uniqueness, CandidateUniquenessEntry.class));

        this.client.transactWriteItems(transactionBuilder.build());
        var fetched = this.candidateTable.getItem(insert);
        return new CandidateWithIdentifier(fetched.getCandidate(), fetched.getIdentifier());
    }

    public CandidateWithIdentifier update(UUID identifier, Candidate candidate) {
        var insert = new CandidateDao(identifier, candidate);

        this.candidateTable.putItem(insert);
        var fetched = this.candidateTable.getItem(insert);
        return new CandidateWithIdentifier(fetched.getCandidate(), fetched.getIdentifier());
    }

    public Optional<CandidateWithIdentifier> findById(UUID id) {
        var queryObj = new CandidateDao(id, Candidate.builder().build());
        var fetched = this.candidateTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(CandidateDao::toCandidateWithIdentifier);
    }

    public CandidateWithIdentifier getById(UUID id) {
        var queryObj = new CandidateDao(id, Candidate.builder().build());
        var fetched = this.candidateTable.getItem(queryObj);
        return fetched.toCandidateWithIdentifier();
    }

    public Optional<CandidateWithIdentifier> findByPublicationId(URI publicationId) {
        var query = QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                                                                          .partitionValue(publicationId.toString())
                                                                          .sortValue(publicationId.toString())
                                                                          .build()))
                        .consistentRead(false)
                        .build();

        var result = this.publicationIdIndex.query(query);

        var users = result.stream()
                        .map(Page::items)
                        .flatMap(Collection::stream)
                        .map(CandidateDao::toCandidateWithIdentifier)
                        .toList();

        return attempt(() -> users.get(0)).toOptional();
    }

    public Optional<ApprovalStatusWithIdentifier> findApprovalByIdAndInstitutionId(UUID identifier, URI uri) {
        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                                         .queryConditional(QueryConditional.keyEqualTo(
                                             Key.builder().partitionValue(CandidateDao.PK(identifier.toString()))
                                                 .sortValue(ApprovalStatusDao.SK(uri.toString())).build()
                                         ))
                                         .consistentRead(false)
                                         .build();
        PageIterable<ApprovalStatusDao> result = approvalStatusTable.query(query);
        return result.items()
                   .stream().map(ApprovalStatusDao::toApprovalStatusWithIdentifier)
                   .findFirst();
    }

    public void updateApprovalStatus(UUID identifier, ApprovalStatus newStatus) {
        var insert = new ApprovalStatusDao(identifier, newStatus);
        approvalStatusTable.putItem(insert);
    }

    private static <T> TransactPutItemEnhancedRequest<T> insertTransaction(T insert, Class<T> clazz) {
        return TransactPutItemEnhancedRequest.builder(clazz)
                   .item(insert)
                   .conditionExpression(uniquePrimaryKeysExpression())
                   .build();
    }
}