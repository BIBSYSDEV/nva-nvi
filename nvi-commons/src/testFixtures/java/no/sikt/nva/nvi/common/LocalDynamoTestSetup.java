package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_1_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR_HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SECONDARY_INDEX_YEAR_RANGE_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;

import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Assertions;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

public class LocalDynamoTestSetup {

  public static final int SINGLE_TABLE_EXPECTED = 1;
  private static final Long CAPACITY_DOES_NOT_MATTER = 1000L;
  protected DynamoDbClient localDynamo;

  public DynamoDbClient initializeTestDatabase() {

    var localDynamo = DynamoDBEmbedded.create().dynamoDbClient();

    var tableName = NVI_TABLE_NAME;
    var createTableResult = createTable(localDynamo, tableName);
    var tableDescription = createTableResult.tableDescription();
    Assertions.assertEquals(tableName, tableDescription.tableName());
    assertThatTableKeySchemaContainsBothKeys(tableDescription.keySchema());
    Assertions.assertEquals(TableStatus.ACTIVE, tableDescription.tableStatus());
    MatcherAssert.assertThat(tableDescription.tableArn(), StringContains.containsString(tableName));

    var tables = localDynamo.listTables();
    Assertions.assertEquals(SINGLE_TABLE_EXPECTED, tables.tableNames().size());
    return localDynamo;
  }

  protected ScanResponse scanDB() {
    return localDynamo.scan(ScanRequest.builder().tableName(NVI_TABLE_NAME).build());
  }

  private static CreateTableResponse createTable(DynamoDbClient client, String tableName) {
    List<AttributeDefinition> attributeDefinitions = defineKeyAttributes();
    List<KeySchemaElement> keySchema = defineKeySchema();
    ProvisionedThroughput provisionedthroughput = provisionedThroughputForLocalDatabase();

    CreateTableRequest request =
        CreateTableRequest.builder()
            .tableName(tableName)
            .attributeDefinitions(attributeDefinitions)
            .keySchema(keySchema)
            .provisionedThroughput(provisionedthroughput)
            .globalSecondaryIndexes(
                newGsi(
                    SECONDARY_INDEX_PUBLICATION_ID,
                    SECONDARY_INDEX_1_HASH_KEY,
                    SECONDARY_INDEX_1_RANGE_KEY),
                newGsi(
                    SECONDARY_INDEX_YEAR,
                    SECONDARY_INDEX_YEAR_HASH_KEY,
                    SECONDARY_INDEX_YEAR_RANGE_KEY))
            .build();

    return client.createTable(request);
  }

  private static List<KeySchemaElement> defineKeySchema() {
    List<KeySchemaElement> keySchemaElements = new ArrayList<>();
    keySchemaElements.add(
        KeySchemaElement.builder().attributeName(HASH_KEY).keyType(KeyType.HASH).build());
    keySchemaElements.add(
        KeySchemaElement.builder().attributeName(SORT_KEY).keyType(KeyType.RANGE).build());
    return keySchemaElements;
  }

  private static List<AttributeDefinition> defineKeyAttributes() {
    List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
    attributeDefinitions.add(createAttributeDefinition(HASH_KEY));
    attributeDefinitions.add(createAttributeDefinition(SORT_KEY));
    attributeDefinitions.add(createAttributeDefinition(SECONDARY_INDEX_1_HASH_KEY));
    attributeDefinitions.add(createAttributeDefinition(SECONDARY_INDEX_1_RANGE_KEY));
    attributeDefinitions.add(createAttributeDefinition(SECONDARY_INDEX_YEAR_HASH_KEY));
    attributeDefinitions.add(createAttributeDefinition(SECONDARY_INDEX_YEAR_RANGE_KEY));
    return attributeDefinitions;
  }

  private static AttributeDefinition createAttributeDefinition(String attributeName) {
    return AttributeDefinition.builder()
        .attributeName(attributeName)
        .attributeType(ScalarAttributeType.S)
        .build();
  }

  private static ProvisionedThroughput provisionedThroughputForLocalDatabase() {
    return ProvisionedThroughput.builder()
        .readCapacityUnits(CAPACITY_DOES_NOT_MATTER)
        .writeCapacityUnits(CAPACITY_DOES_NOT_MATTER)
        .build();
  }

  private static GlobalSecondaryIndex newGsi(String indexName, String hashKey, String rangeKey) {
    var provisionedthroughput = provisionedThroughputForLocalDatabase();

    return GlobalSecondaryIndex.builder()
        .indexName(indexName)
        .keySchema(
            KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build(),
            KeySchemaElement.builder().attributeName(rangeKey).keyType(KeyType.RANGE).build())
        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
        .provisionedThroughput(provisionedthroughput)
        .build();
  }

  private void assertThatTableKeySchemaContainsBothKeys(List<KeySchemaElement> tableKeySchema) {
    MatcherAssert.assertThat(tableKeySchema.toString(), StringContains.containsString(HASH_KEY));
    MatcherAssert.assertThat(tableKeySchema.toString(), StringContains.containsString(SORT_KEY));
  }
}
