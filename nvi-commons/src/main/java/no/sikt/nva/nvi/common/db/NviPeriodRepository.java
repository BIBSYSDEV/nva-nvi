package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.ApplicationConstants.NVI_TABLE_NAME;
import java.util.Optional;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviPeriodRepository extends DynamoRepository  {
    private final DynamoDbTable<NviPeriodDao> nviPeriodTable;

    public NviPeriodRepository(DynamoDbClient client) {
        super(client);
        this.nviPeriodTable = this.client.table(NVI_TABLE_NAME, NviPeriodDao.TABLE_SCHEMA);
    }


    public NviPeriod save(NviPeriod nviPeriod) {
        var nviPeriodDao = new NviPeriodDao(nviPeriod);

        this.nviPeriodTable.putItem(nviPeriodDao);

        var fetched = this.nviPeriodTable.getItem(nviPeriodDao);
        return fetched.getNviPeriod();
    }

    public Optional<NviPeriod> findByYear(String year) {
        var queryObj = new NviPeriodDao(new NviPeriod.Builder().withPublishingYear(year).build());
        var fetched = this.nviPeriodTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(NviPeriodDao::getNviPeriod);

    }
}