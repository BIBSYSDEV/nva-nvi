package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.ApplicationConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_1_HASH_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_1_RANGE_KEY;
import static no.sikt.nva.nvi.common.ApplicationConstants.SECONDARY_INDEX_PUBLICATION_ID;
import static no.sikt.nva.nvi.common.ApplicationConstants.SORT_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.common.ApplicationConstants;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

public class LocalDynamoTest {

    protected DynamoDbClient localDynamo;
    private static final Long CAPACITY_DOES_NOT_MATTER = 1000L;
    public static final int SINGLE_TABLE_EXPECTED = 1;

    public DynamoDbClient initializeTestDatabase() {

        DynamoDbClient localDynamo = DynamoDBEmbedded.create().dynamoDbClient();


        String tableName = ApplicationConstants.NVI_TABLE_NAME;
        CreateTableResponse createTableResult = createTable(localDynamo, tableName);
        TableDescription tableDescription = createTableResult.tableDescription();
        assertEquals(tableName, tableDescription.tableName());
        assertThatTableKeySchemaContainsBothKeys(tableDescription.keySchema());
        assertEquals(TableStatus.ACTIVE, tableDescription.tableStatus());
        assertThat(tableDescription.tableArn(), containsString(tableName));

        ListTablesResponse tables = localDynamo.listTables();
        assertEquals(SINGLE_TABLE_EXPECTED, tables.tableNames().size());
        return localDynamo;
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
                    newGsi(SECONDARY_INDEX_PUBLICATION_ID,
                           SECONDARY_INDEX_1_HASH_KEY,
                           SECONDARY_INDEX_1_RANGE_KEY)
                )
                .build();

        return client.createTable(request);
    }

    private static List<KeySchemaElement> defineKeySchema() {
        List<KeySchemaElement> keySchemaElements = new ArrayList<>();
        keySchemaElements
            .add(KeySchemaElement.builder().attributeName(HASH_KEY).keyType(KeyType.HASH).build());
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
        return attributeDefinitions;
    }

    private static AttributeDefinition createAttributeDefinition(String attributeName) {
        return AttributeDefinition.builder()
                   .attributeName(attributeName)
                   .attributeType(ScalarAttributeType.S).build();
    }

    private static ProvisionedThroughput provisionedThroughputForLocalDatabase() {
        return ProvisionedThroughput.builder()
                   .readCapacityUnits(CAPACITY_DOES_NOT_MATTER)
                   .writeCapacityUnits(CAPACITY_DOES_NOT_MATTER)
                   .build();
    }

    private static GlobalSecondaryIndex newGsi(String searchUsersByInstitutionIndexName,
                                               String secondaryIndex1HashKey,
                                               String secondaryIndex1RangeKey) {
        ProvisionedThroughput provisionedthroughput = provisionedThroughputForLocalDatabase();

        return GlobalSecondaryIndex
                   .builder()
                   .indexName(searchUsersByInstitutionIndexName)
                   .keySchema(
                       KeySchemaElement.builder().attributeName(secondaryIndex1HashKey).keyType(KeyType.HASH).build(),
                       KeySchemaElement.builder().attributeName(secondaryIndex1RangeKey).keyType(KeyType.RANGE).build()
                   )
                   .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                   .provisionedThroughput(provisionedthroughput)
                   .build();
    }

    private void assertThatTableKeySchemaContainsBothKeys(List<KeySchemaElement> tableKeySchema) {
        assertThat(tableKeySchema.toString(), containsString(HASH_KEY));
        assertThat(tableKeySchema.toString(), containsString(SORT_KEY));
    }


}
