package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.BeginsWithConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class PeriodRepository extends DynamoRepository {

    public static final String PERIOD = "PERIOD";
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    public static final String SCIENTIFIC_INDEX_API_PATH = "scientific-index";
    public static final String PERIOD_PATH = "period";
    protected final DynamoDbTable<NviPeriodDao> nviPeriodTable;

    public PeriodRepository(DynamoDbClient client) {
        super(client);
        this.nviPeriodTable = this.client.table(NVI_TABLE_NAME, NviPeriodDao.TABLE_SCHEMA);
    }

    public DbNviPeriod save(DbNviPeriod nviPeriod) {
        var periodWithId = nviPeriod.copy().id(createId(nviPeriod)).build();
        var nviPeriodDao = NviPeriodDao.builder()
                               .identifier(nviPeriod.publishingYear())
                               .nviPeriod(periodWithId)
                               .version(randomUUID().toString())
                               .build();

        this.nviPeriodTable.putItem(nviPeriodDao);

        var fetched = this.nviPeriodTable.getItem(nviPeriodDao);
        return fetched.nviPeriod();
    }

    private URI createId(DbNviPeriod nviPeriod) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(SCIENTIFIC_INDEX_API_PATH)
                   .addChild(PERIOD_PATH)
                   .addChild(nviPeriod.publishingYear())
                   .getUri();
    }

    public Optional<DbNviPeriod> findByPublishingYear(String publishingYear) {
        var queryObj = NviPeriodDao.builder()
                           .nviPeriod(DbNviPeriod.builder().publishingYear(publishingYear).build())
                           .identifier(publishingYear)
                           .build();
        var fetched = this.nviPeriodTable.getItem(queryObj);
        return Optional.ofNullable(fetched).map(NviPeriodDao::nviPeriod);
    }

    public List<DbNviPeriod> getPeriods() {
        return this.nviPeriodTable.query(beginsWithPeriodQuery()).stream()
                   .map(Page::items)
                   .flatMap(Collection::stream)
                   .map(NviPeriodDao::nviPeriod)
                   .toList();
    }

    protected static BeginsWithConditional beginsWithPeriodQuery() {
        return new BeginsWithConditional(Key.builder()
                                             .partitionValue(PERIOD)
                                             .sortValue(PERIOD)
                                             .build());
    }
}