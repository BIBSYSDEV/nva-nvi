package no.sikt.nva.nvi.common.db;

public interface DynamoEntryWithRangeKey extends Typed {
    String FIELD_DELIMITER = "#";

    String primaryKeyHashKey();

    String primaryKeyRangeKey();


}