package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.ApplicationConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SORT_KEY;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

public abstract class UniquenessEntry implements DynamoEntryWithRangeKey {
    public static final String DESERIALIZATION_ERROR_MESSAGE = "UniquenessEntries are not supposed to be deserialized";
    private String partitionKey;
    private String sortKey;

    @JacocoGenerated
    public UniquenessEntry() {
    }

    public UniquenessEntry(String identifier) {
        this.partitionKey = getType() + FIELD_DELIMITER + identifier;
        this.sortKey = partitionKey;
    }

    @JacocoGenerated
    @Override
    @DynamoDbPartitionKey
    @DynamoDbAttribute(HASH_KEY)
    public String getPrimaryKeyHashKey() {
        return partitionKey;
    }

    @Override
    @JacocoGenerated
    public void setPrimaryKeyHashKey(String primaryHashKey) {
        this.partitionKey = primaryHashKey;

    }

    @Override
    @JacocoGenerated
    @DynamoDbSortKey
    @DynamoDbAttribute(SORT_KEY)
    public String getPrimaryKeyRangeKey() {
        return sortKey;
    }

    @Override
    @JacocoGenerated
    public void setPrimaryKeyRangeKey(String primaryRangeKey) {
        this.sortKey = primaryRangeKey;

    }
}
