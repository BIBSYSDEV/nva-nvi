package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.BeginsWithConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class PeriodRepository extends DynamoRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(PeriodRepository.class);
  protected final DynamoDbTable<NviPeriodDao> nviPeriodTable;

  public PeriodRepository(DynamoDbClient client) {
    super(client);
    this.nviPeriodTable = this.client.table(NVI_TABLE_NAME, NviPeriodDao.TABLE_SCHEMA);
  }

  public void save(NviPeriodDao period) {
    LOGGER.info("Saving period: {}", period);
    nviPeriodTable.putItem(period.withMutatedVersion());
  }

  public Optional<NviPeriodDao> findByPublishingYear(String publishingYear) {
    LOGGER.info("Finding period by publishing year: {}", publishingYear);
    var periodKey = createPeriodKey(publishingYear);
    return Optional.ofNullable(nviPeriodTable.getItem(getByKey(periodKey)));
  }

  public List<NviPeriodDao> getPeriods() {
    LOGGER.info("Getting all periods");
    var query =
        QueryEnhancedRequest.builder()
            .queryConditional(beginsWithPeriodQuery())
            .consistentRead(true)
            .build();
    return nviPeriodTable.query(query).stream()
        .map(Page::items)
        .flatMap(Collection::stream)
        .toList();
  }

  public CompletableFuture<List<NviPeriodDao>> getPeriodsAsync() {
    LOGGER.info("Getting all periods with async wrapper");
    return CompletableFuture.supplyAsync(this::getPeriods);
  }

  private static BeginsWithConditional beginsWithPeriodQuery() {
    return new BeginsWithConditional(
        Key.builder().partitionValue(NviPeriodDao.TYPE).sortValue(NviPeriodDao.TYPE).build());
  }

  private static Key createPeriodKey(String publishingYear) {
    return Key.builder()
        .partitionValue(NviPeriodDao.TYPE)
        .sortValue(NviPeriodDao.createSortKey(publishingYear))
        .build();
  }
}
