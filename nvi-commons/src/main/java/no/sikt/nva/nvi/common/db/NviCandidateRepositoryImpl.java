package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.dto.CandidateDb;
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

    private CandidateDb attemptToFetchObject(CandidateDb queryObject) {
        throw new UnsupportedOperationException("Not implemented yet");
        /*
        CandidateDao candidateDao = attempt(() -> CandidateDao.fromCandidateDto(queryObject))
                              .map(this::fetchItem)
                              .orElseThrow(DynamoRepository::handleError);
        return nonNull(candidateDao) ? candidateDao.toCandidateDto() : null;

         */
    }

    @Override
    public CandidateDb save(CandidateDb candidate) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<CandidateDb> findByPublicationId(URI publicationId) {
        return Optional.empty();
    }
}