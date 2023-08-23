package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.ApplicationConstants.PRIMARY_KEY;
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
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
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
                .build();

        return client.createTable(request);
    }

    private static List<KeySchemaElement> defineKeySchema() {
        List<KeySchemaElement> keySchemaElements = new ArrayList<>();
        keySchemaElements
            .add(KeySchemaElement.builder().attributeName(PRIMARY_KEY).keyType(KeyType.HASH).build());
        keySchemaElements.add(
            KeySchemaElement.builder().attributeName(SORT_KEY).keyType(KeyType.RANGE).build());
        return keySchemaElements;
    }

    private static List<AttributeDefinition> defineKeyAttributes() {
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        attributeDefinitions.add(createAttributeDefinition(PRIMARY_KEY));
        attributeDefinitions.add(createAttributeDefinition(SORT_KEY));
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

    private void assertThatTableKeySchemaContainsBothKeys(List<KeySchemaElement> tableKeySchema) {
        assertThat(tableKeySchema.toString(), containsString(PRIMARY_KEY));
        assertThat(tableKeySchema.toString(), containsString(SORT_KEY));
    }


}
