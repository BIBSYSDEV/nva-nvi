package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.BeginsWithConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

public class PeriodRepository extends DynamoRepository {

  public static final String PERIOD = "PERIOD";
  private static final Logger LOGGER = LoggerFactory.getLogger(PeriodRepository.class);
  protected final DynamoDbTable<NviPeriodDao> nviPeriodTable;

  public PeriodRepository(DynamoDbClient client) {
    super(client);
    this.nviPeriodTable = this.client.table(NVI_TABLE_NAME, NviPeriodDao.TABLE_SCHEMA);
  }

  // FIXME: Make this void
  public NviPeriodDao save(NviPeriodDao period) {
    LOGGER.info("Saving period: {}", period);
    var updatedPeriod = period.copy().version(randomUUID().toString()).build();

    this.nviPeriodTable.putItem(updatedPeriod);

    return this.nviPeriodTable.getItem(updatedPeriod);
  }

  // FIXME: Remove
  public DbNviPeriod save(DbNviPeriod nviPeriod) {
    LOGGER.info("Saving period: {}", nviPeriod);
    var nviPeriodDao =
        NviPeriodDao.builder()
            .identifier(nviPeriod.publishingYear())
            .nviPeriod(nviPeriod)
            .version(randomUUID().toString())
            .build();

    this.nviPeriodTable.putItem(nviPeriodDao);

    var fetched = this.nviPeriodTable.getItem(nviPeriodDao);
    return fetched.nviPeriod();
  }

  // FIXME: Temp
  public Optional<NviPeriodDao> findByPublishingYearAsDao(String publishingYear) {
    var queryObj =
        NviPeriodDao.builder()
            .nviPeriod(DbNviPeriod.builder().publishingYear(publishingYear).build())
            .identifier(publishingYear)
            .build();
    var fetched = this.nviPeriodTable.getItem(queryObj);
    return Optional.ofNullable(fetched);
  }

  public Optional<DbNviPeriod> findByPublishingYear(String publishingYear) {
    var queryObj =
        NviPeriodDao.builder()
            .nviPeriod(DbNviPeriod.builder().publishingYear(publishingYear).build())
            .identifier(publishingYear)
            .build();
    var fetched = this.nviPeriodTable.getItem(queryObj);
    return Optional.ofNullable(fetched).map(NviPeriodDao::nviPeriod);
  }

  @Deprecated // FIXME
  public List<NviPeriodDao> getPeriods() {
    return this.nviPeriodTable.query(beginsWithPeriodQuery()).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .toList();
  }

  public static List<NviPeriodDao> getPeriods(DynamoDbTable<NviPeriodDao> periodTable) {
    return periodTable.query(beginsWithPeriodQuery()).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .toList();
  }

  public static QueryRequest getPeriodsRequest() {
    return queryByPartitionKey(PERIOD);
  }

  @Deprecated
  public static BeginsWithConditional beginsWithPeriodQuery() {
    return new BeginsWithConditional(
        Key.builder().partitionValue(PERIOD).sortValue(PERIOD).build());
  }
}
