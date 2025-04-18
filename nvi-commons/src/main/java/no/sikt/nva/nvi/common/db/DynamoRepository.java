package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.DatabaseConstants.HASH_KEY;
import static no.sikt.nva.nvi.common.DatabaseConstants.SORT_KEY;
import static no.sikt.nva.nvi.common.utils.ApplicationConstants.REGION;

import java.util.Map;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoRepository {
  private static final String PARTITION_KEY_NAME_PLACEHOLDER = "#partitionKey";
  private static final String SORT_KEY_NAME_PLACEHOLDER = "#sortKey";
  final DynamoDbEnhancedClient client;
  final DynamoDbClient defaultClient;

  protected DynamoRepository(DynamoDbClient client) {
    this.defaultClient = client;
    this.client = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
  }

  private static String keyNotExistsCondition() {
    return String.format(
        "attribute_not_exists(%s) AND attribute_not_exists(%s)",
        PARTITION_KEY_NAME_PLACEHOLDER, SORT_KEY_NAME_PLACEHOLDER);
  }

  private static Map<String, String> primaryKeyEqualityConditionAttributeNames() {
    return Map.of(
        PARTITION_KEY_NAME_PLACEHOLDER, HASH_KEY,
        SORT_KEY_NAME_PLACEHOLDER, SORT_KEY);
  }

  protected static Expression uniquePrimaryKeysExpression() {
    return Expression.builder()
        .expression(keyNotExistsCondition())
        .expressionNames(primaryKeyEqualityConditionAttributeNames())
        .build();
  }

  @JacocoGenerated
  public static DynamoDbClient defaultDynamoClient() {
    return DynamoDbClient.builder()
        .httpClient(UrlConnectionHttpClient.create())
        .credentialsProvider(DefaultCredentialsProvider.create())
        .region(REGION)
        .build();
  }
}
