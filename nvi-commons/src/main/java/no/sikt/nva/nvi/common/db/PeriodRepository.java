package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

  public void save(NviPeriodDao period) {
    LOGGER.info("Saving period: {}", period);
    var updatedPeriod = period.copy().version(randomUUID().toString()).build();
    nviPeriodTable.putItem(updatedPeriod);
  }

  public Optional<NviPeriodDao> findByPublishingYear(String publishingYear) {
    LOGGER.info("Finding period by publishing year: {}", publishingYear);
    var queryObj =
        NviPeriodDao.builder()
            .nviPeriod(DbNviPeriod.builder().publishingYear(publishingYear).build())
            .identifier(publishingYear)
            .build();
    var fetched = this.nviPeriodTable.getItem(queryObj);
    return Optional.ofNullable(fetched);
  }

  public List<NviPeriodDao> getPeriods() {
    LOGGER.info("Getting all periods");
    return this.nviPeriodTable.query(beginsWithPeriodQuery()).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .toList();
  }

  public CompletableFuture<List<NviPeriodDao>> getPeriodsAsync() {
    LOGGER.info("Getting all periods with async wrapper");
    return CompletableFuture.supplyAsync(() -> getPeriods());
  }

  // FIXME: Remove this
  public static List<NviPeriodDao> getPeriods(DynamoDbTable<NviPeriodDao> periodTable) {
    return periodTable.query(beginsWithPeriodQuery()).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .toList();
  }

  public static QueryRequest getPeriodsRequest() {
    return queryByPartitionKey(PERIOD);
  }

  public static BeginsWithConditional beginsWithPeriodQuery() {
    return new BeginsWithConditional(
        Key.builder().partitionValue(PERIOD).sortValue(PERIOD).build());
  }
}
