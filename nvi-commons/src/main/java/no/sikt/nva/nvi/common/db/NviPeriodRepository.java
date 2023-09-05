package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import java.util.Optional;
import no.sikt.nva.nvi.common.model.business.DbNviPeriod;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviPeriodRepository extends DynamoRepository {

    private final DynamoDbTable<NviPeriodDao> nviPeriodTable;

    public NviPeriodRepository(DynamoDbClient client) {
        super(client);
        this.nviPeriodTable = this.client.table(NVI_TABLE_NAME, NviPeriodDao.TABLE_SCHEMA);
    }

    public DbNviPeriod save(DbNviPeriod nviPeriod) {
        var nviPeriodDao = new NviPeriodDao(nviPeriod);

        this.nviPeriodTable.putItem(nviPeriodDao);

        var fetched = this.nviPeriodTable.getItem(nviPeriodDao);
        return fetched.nviPeriod();
    }

    public Optional<DbNviPeriod> findByPublishingYear(String publishingYear) {
        var queryObj = new NviPeriodDao(DbNviPeriod.builder().publishingYear(publishingYear).build());
        var fetched = this.nviPeriodTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(NviPeriodDao::nviPeriod);
    }
}