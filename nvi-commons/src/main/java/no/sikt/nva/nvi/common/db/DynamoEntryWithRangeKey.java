package no.sikt.nva.nvi.common.db;

public interface DynamoEntryWithRangeKey extends Typed {
    String FIELD_DELIMITER = "#";

    String getPrimaryKeyHashKey();

    /**
     * Setter of the primary hash key. This method is supposed to be used only by when deserializing an item using Json
     * serializer.
     *
     * @param primaryRangeKey the primary hash key.
     */
    void setPrimaryKeyHashKey(String primaryHashKey);

    String getPrimaryKeyRangeKey();

    /**
     * Setter of the primary range key. This method is supposed to be used only by when deserializing an item using Json
     * serializer.
     *
     * @param primaryRangeKey the primary range key.
     */
    void setPrimaryKeyRangeKey(String primaryRangeKey);


}