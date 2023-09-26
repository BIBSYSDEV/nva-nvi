package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.model.PeriodDao;
import no.sikt.nva.nvi.common.db.model.PeriodDao.PeriodData;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.BeginsWithConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviPeriodRepository extends DynamoRepository {

    public static final String PERIOD = "PERIOD";
    private final DynamoDbTable<PeriodDao> nviPeriodTable;

    public NviPeriodRepository(DynamoDbClient client) {
        super(client);
        this.nviPeriodTable = this.client.table(NVI_TABLE_NAME, PeriodDao.TABLE_SCHEMA);
    }

    public PeriodData save(PeriodData nviPeriod) {
        var nviPeriodDao = new PeriodDao(nviPeriod);

        this.nviPeriodTable.putItem(nviPeriodDao);

        var fetched = this.nviPeriodTable.getItem(nviPeriodDao);
        return fetched.nviPeriod();
    }

    public Optional<PeriodData> findByPublishingYear(String publishingYear) {
        var queryObj = new PeriodDao(PeriodData.builder().publishingYear(publishingYear).build());
        var fetched = this.nviPeriodTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(PeriodDao::nviPeriod);
    }

    public List<PeriodData> getPeriods() {
        return this.nviPeriodTable.query(beginsWithPeriodQuery()).stream()
                   .map(Page::items)
                   .flatMap(Collection::stream)
                   .map(PeriodDao::nviPeriod)
                   .toList();
    }

    private static BeginsWithConditional beginsWithPeriodQuery() {
        return new BeginsWithConditional(Key.builder()
                                             .partitionValue(PERIOD)
                                             .sortValue(PERIOD)
                                             .build());
    }
}