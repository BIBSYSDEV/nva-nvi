package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviCandidateRepositoryImpl extends DynamoRepository implements NviCandidateRepository  {

    private final DynamoDbTable<CandidateDao> table;
    private final DynamoDbIndex<CandidateDao> publicationIdIndex;

    public NviCandidateRepositoryImpl(DynamoDbClient client) {
        super(client);
        this.table = this.client.table(NVI_TABLE_NAME, CandidateDao.TABLE_SCHEMA);
        this.publicationIdIndex = this.table.index(SECONDARY_INDEX_PUBLICATION_ID);
    }

    @Override
    public CandidateWithIdentifier save(Candidate candidate) {
        var uuid = UUID.randomUUID();
        var insert = new CandidateDao(uuid, candidate);
        this.table.putItem(insert);
        var fetched = this.table.getItem(insert);
        return new CandidateWithIdentifier(fetched.getCandidate(), fetched.getIdentifier());
    }

    @Override
    public Optional<CandidateWithIdentifier> findById(UUID id) {
        var queryObj = new CandidateDao(id, new Candidate.Builder().build());
        var fetched = this.table.getItem(queryObj);
        return Optional.ofNullable(fetched).map(CandidateDao::toCandidateWithIdentifier);

    }

    @Override
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
                        .collect(Collectors.toList());

        return attempt(() -> users.get(0)).toOptional();
    }
}