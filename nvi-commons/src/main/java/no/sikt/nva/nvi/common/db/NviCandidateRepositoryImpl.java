package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.Candidate;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviCandidateRepositoryImpl extends DynamoRepository implements NviCandidateRepository  {

    private final DynamoDbTable<CandidateDao> table;

    public NviCandidateRepositoryImpl(DynamoDbClient client) {
        super(client);
        this.table = this.client.table(NVI_TABLE_NAME, CandidateDao.TABLE_SCHEMA);
    }

    @Override
    public Candidate save(Candidate candidate) {
        var insert = CandidateDao.fromCandidateDto(candidate);
        this.table.putItem(insert);
        var fetched = this.table.getItem(CandidateDao.fromCandidateDto(candidate));
        return fetched.getCandidateDb();
    }

    @Override
    public Optional<Candidate> findByPublicationId(URI publicationId) {
        var queryObj = new CandidateDao(CandidateDao.getDocIdFromUri(publicationId), new Candidate.Builder().build());
        var fetched = this.table.getItem(queryObj);
        return Optional.ofNullable(fetched).map(CandidateDao::getCandidateDb);
    }
}