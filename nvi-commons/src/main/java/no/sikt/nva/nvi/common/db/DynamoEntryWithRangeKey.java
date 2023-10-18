package no.sikt.nva.nvi.common.db;

public interface DynamoEntryWithRangeKey extends Typed {
    String FIELD_DELIMITER = "#";
    String VERSION_FIELD_NAME = "version";

    String primaryKeyHashKey();

    String primaryKeyRangeKey();

    String version();
}