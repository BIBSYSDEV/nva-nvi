package no.sikt.nva.nvi.common.db;

import no.sikt.nva.nvi.common.db.CandidateDao.Builder;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@SuppressWarnings({"PMD.GodClass", "PMD.ExcessivePublicCount"})
@DynamoDbBean(converterProviders = { NviCandidateConverterProvider.class, DefaultAttributeConverterProvider.class})
public class CandidateDao implements DynamoEntryWithRangeKey, WithCopy<Builder> {

    public static final TableSchema<CandidateDao> TABLE_SCHEMA = TableSchema.fromClass(CandidateDao.class);

    public CandidateDao() {
        super();
    }

    @Override
    public String getPrimaryKeyHashKey() {
        return null;
    }

    @Override
    public void setPrimaryKeyHashKey(String primaryRangeKey) {

    }

    @Override
    public String getPrimaryKeyRangeKey() {
        return null;
    }

    @Override
    public void setPrimaryKeyRangeKey(String primaryRangeKey) {

    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public Builder copy() {
        return null;
    }

    public static class Builder {

    }

}
