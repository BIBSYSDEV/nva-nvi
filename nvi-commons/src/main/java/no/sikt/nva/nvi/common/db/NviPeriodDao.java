package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import no.sikt.nva.nvi.common.db.converters.NviPeriodConverterProvider;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@SuppressWarnings({"PMD.GodClass", "PMD.ExcessivePublicCount"})
@DynamoDbBean(converterProviders = { NviPeriodConverterProvider.class, DefaultAttributeConverterProvider.class})
public class NviPeriodDao extends Dao implements DynamoEntryWithRangeKey {

    public static final TableSchema<NviPeriodDao> TABLE_SCHEMA = TableSchema.fromClass(NviPeriodDao.class);

    public static final String TYPE = "PERIOD";
    private String identifier;
    private NviPeriod nviPeriod;

    public NviPeriodDao() {
        super();
    }

    public NviPeriodDao(NviPeriod nviPeriod) {
        super();
        this.identifier = nviPeriod.publishingYear();
        this.nviPeriod = nviPeriod;
    }

    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String getPrimaryKeyHashKey() {
        return String.join(DynamoEntryWithRangeKey.FIELD_DELIMITER, TYPE, identifier);
    }

    @Override
    public void setPrimaryKeyHashKey(String primaryHashKey) {
        //DO NOTHING
    }

    @Override
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String getPrimaryKeyRangeKey() {
        return getPrimaryKeyHashKey();
    }

    @Override
    public void setPrimaryKeyRangeKey(String primaryRangeKey) {
        //DO NOTHING
    }

    @Override
    @JacocoGenerated
    @DynamoDbAttribute(TYPE_FIELD)
    public String getType() {
        return TYPE;
    }

    @DynamoDbAttribute(DATA_FIELD)
    public NviPeriod getNviPeriod() {
        return nviPeriod;
    }

    @JacocoGenerated
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setNviPeriod(NviPeriod nviPeriod) {
        this.nviPeriod = nviPeriod;
    }
}
