package no.sikt.nva.nvi.common;

/**
 * Utility to set up fake environment variables for testing purposes. Keep this in sync with the
 * actual environment variables defined in template.yaml.
 */
public enum EnvironmentFixtures {
  // Global environment variables
  API_HOST("api.fake.nva.aws.unit.no"),
  BACKEND_CLIENT_AUTH_URL("https://api.fake.nva.aws.unit.no/auth"),
  BACKEND_CLIENT_SECRET_NAME("BackendCognitoClientCredentials"),
  COGNITO_AUTHORIZER_URLS("http://localhost:3000,https://localhost:3000"),
  CUSTOM_DOMAIN_BASE_PATH("scientific-index"),
  NVI_TABLE_NAME("nvi-table-name"),
  SEARCH_INFRASTRUCTURE_API_HOST("https://api.fake.sws.aws.sikt.no"),
  SEARCH_INFRASTRUCTURE_AUTH_URI("https://sws-auth.fake.auth.eu-west-1.amazoncognito.com"),

  // Handler-specific environment variables
  CANDIDATE_QUEUE_URL("http://localhost:3000/candidate-queue"),
  DB_EVENTS_QUEUE_URL("http://localhost:3000/db-events-queue"),
  EXPANDED_RESOURCES_BUCKET("persisted-resources-bucket"),
  INDEX_DLQ("http://localhost:3000/index-dlq"),
  UPSERT_CANDIDATE_DLQ_QUEUE_URL("http://localhost:3000/upsert-candidate-dlq");

  private final String value;

  EnvironmentFixtures(String value) {
    this.value = value;
  }

  public String getKey() {
    return this.name();
  }

  public String getValue() {
    return value;
  }

  public static FakeEnvironment.Builder getDefaultEnvironmentBuilder() {
    return FakeEnvironment.builder()
        .with(API_HOST)
        .with(BACKEND_CLIENT_AUTH_URL)
        .with(BACKEND_CLIENT_SECRET_NAME)
        .with(COGNITO_AUTHORIZER_URLS)
        .with(CUSTOM_DOMAIN_BASE_PATH)
        .with(NVI_TABLE_NAME)
        .with(SEARCH_INFRASTRUCTURE_API_HOST)
        .with(SEARCH_INFRASTRUCTURE_AUTH_URI);
  }

  public static FakeEnvironment getEvaluateNviCandidateHandlerEnvironment() {
    return getDefaultEnvironmentBuilder()
        .with(EXPANDED_RESOURCES_BUCKET)
        .with(CANDIDATE_QUEUE_URL)
        .build();
  }

  public static FakeEnvironment getUpsertNviCandidateHandlerEnvironment() {
    return getDefaultEnvironmentBuilder().with(UPSERT_CANDIDATE_DLQ_QUEUE_URL).build();
  }
}
