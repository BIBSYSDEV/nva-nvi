package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.dynamodb.services.local.embedded.DynamoDBEmbedded;

public class LocalDynamoTestSetup {

  private static final Long CAPACITY_DOES_NOT_MATTER = 1000L;
  private static final ProvisionedThroughput LOCAL_THROUGHPUT =
      ProvisionedThroughput.builder()
          .readCapacityUnits(CAPACITY_DOES_NOT_MATTER)
          .writeCapacityUnits(CAPACITY_DOES_NOT_MATTER)
          .build();

  public static DynamoDbClient initializeTestDatabase(String tableName) {
    var localDynamo = DynamoDBEmbedded.create(null, true).dynamoDbClient();
    localDynamo.createTable(buildCreateTableRequest(tableName));
    return localDynamo;
  }

  private static CreateTableRequest buildCreateTableRequest(String tableName) {
    return CreateTableRequest.builder()
        .tableName(tableName)
        .attributeDefinitions(
            stringAttribute(HASH_KEY),
            stringAttribute(SORT_KEY),
            stringAttribute(SECONDARY_INDEX_1_HASH_KEY),
            stringAttribute(SECONDARY_INDEX_1_RANGE_KEY),
            stringAttribute(SECONDARY_INDEX_YEAR_HASH_KEY),
            stringAttribute(SECONDARY_INDEX_YEAR_RANGE_KEY))
        .keySchema(keyElement(HASH_KEY, KeyType.HASH), keyElement(SORT_KEY, KeyType.RANGE))
        .provisionedThroughput(LOCAL_THROUGHPUT)
        .globalSecondaryIndexes(
            gsi(
                SECONDARY_INDEX_PUBLICATION_ID,
                SECONDARY_INDEX_1_HASH_KEY,
                SECONDARY_INDEX_1_RANGE_KEY),
            gsi(
                SECONDARY_INDEX_YEAR,
                SECONDARY_INDEX_YEAR_HASH_KEY,
                SECONDARY_INDEX_YEAR_RANGE_KEY))
        .build();
  }

  private static AttributeDefinition stringAttribute(String name) {
    return AttributeDefinition.builder()
        .attributeName(name)
        .attributeType(ScalarAttributeType.S)
        .build();
  }

  private static KeySchemaElement keyElement(String name, KeyType type) {
    return KeySchemaElement.builder().attributeName(name).keyType(type).build();
  }

  private static GlobalSecondaryIndex gsi(String indexName, String hashKey, String rangeKey) {
    return GlobalSecondaryIndex.builder()
        .indexName(indexName)
        .keySchema(keyElement(hashKey, KeyType.HASH), keyElement(rangeKey, KeyType.RANGE))
        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
        .provisionedThroughput(LOCAL_THROUGHPUT)
        .build();
  }
}
