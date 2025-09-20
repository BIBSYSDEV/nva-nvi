package no.sikt.nva.nvi.common;

public final class DatabaseConstants {

  public static final String HASH_KEY = "PrimaryKeyHashKey";
  public static final String SORT_KEY = "PrimaryKeyRangeKey";
  public static final String SECONDARY_INDEX_1_HASH_KEY = "SecondaryIndex1HashKey";
  public static final String SECONDARY_INDEX_1_RANGE_KEY = "SecondaryIndex1RangeKey";
  public static final String SECONDARY_INDEX_PUBLICATION_ID = "SearchByPublicationId";
  public static final String SECONDARY_INDEX_YEAR = "SearchByYear";
  public static final String SECONDARY_INDEX_YEAR_HASH_KEY = "SearchByYearHashKey";
  public static final String SECONDARY_INDEX_YEAR_RANGE_KEY = "SearchByYearRangeKey";
  public static final String DATA_FIELD = "data";
  public static final String IDENTIFIER_FIELD = "identifier";

  /**
   * Custom version field managed manually by the application (UUID as String). Used for BatchScan.
   */
  public static final String VERSION_FIELD = "version";

  /**
   * Auto-incremented version field, used for optimistic locking. Set and enforced automatically on
   * every write operation.
   */
  public static final String REVISION_FIELD = "revision";

  /**
   * Auto-generated timestamp field set automatically on every write operation. Intended for
   * debugging and future plans around re-indexing/BatchScan.
   */
  public static final String LAST_WRITTEN_FIELD = "lastWrittenAt";

  private DatabaseConstants() {}
}
