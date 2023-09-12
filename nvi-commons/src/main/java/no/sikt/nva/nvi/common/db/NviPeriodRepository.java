package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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

    public List<DbNviPeriod> getPeriods() {
        var expression = Expression.builder()
                             .expression("#a = :b")
                             .putExpressionName("#a", "type")
                             .putExpressionValue(":b", AttributeValue.fromS("PERIOD"))
                             .build();
        var scanEnhancedRequest = ScanEnhancedRequest.builder()
                                      .filterExpression(expression)
                                      .build();

        return this.nviPeriodTable.scan(scanEnhancedRequest).stream()
                   .map(Page::items)
                   .flatMap(Collection::stream)
                   .map(NviPeriodDao::nviPeriod)
                   .toList();
    }
}