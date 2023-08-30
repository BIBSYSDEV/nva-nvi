package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviCandidateRepository extends DynamoRepository  {
    private final DynamoDbTable<CandidateDao> candidateTable;
    private final DynamoDbTable<CandidateUniquenessEntry> uniquenessTable;
    private final DynamoDbIndex<CandidateDao> publicationIdIndex;

    public NviCandidateRepository(DynamoDbClient client) {
        super(client);
        this.candidateTable = this.client.table(NVI_TABLE_NAME, CandidateDao.TABLE_SCHEMA);
        this.uniquenessTable = this.client.table(NVI_TABLE_NAME, CandidateUniquenessEntry.TABLE_SCHEMA);
        this.publicationIdIndex = this.candidateTable.index(SECONDARY_INDEX_PUBLICATION_ID);
    }


    public CandidateWithIdentifier save(Candidate candidate) {
        var uuid = UUID.randomUUID();
        var insert = new CandidateDao(uuid, candidate);
        var uniqueness = new CandidateUniquenessEntry(candidate.publicationId().toString());

        var putCandidateRequest = TransactPutItemEnhancedRequest.builder(CandidateDao.class)
                             .item(insert)
                             .conditionExpression(uniquePrimaryKeysExpression())
                             .build();

        var putUniquenessRequest = TransactPutItemEnhancedRequest.builder(CandidateUniquenessEntry.class)
                             .item(uniqueness)
                             .conditionExpression(uniquePrimaryKeysExpression())
                             .build();

        var request = TransactWriteItemsEnhancedRequest.builder()
                          .addPutItem(this.candidateTable, putCandidateRequest)
                          .addPutItem(this.uniquenessTable, putUniquenessRequest)
                          .build();

        this.client.transactWriteItems(request);
        var fetched = this.candidateTable.getItem(insert);
        return new CandidateWithIdentifier(fetched.getCandidate(), fetched.getIdentifier());
    }

    public Optional<CandidateWithIdentifier> findById(UUID id) {
        var queryObj = new CandidateDao(id, new Candidate.Builder().build());
        var fetched = this.candidateTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(CandidateDao::toCandidateWithIdentifier);

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
}