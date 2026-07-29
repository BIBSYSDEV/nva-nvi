package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.COGNITO_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INDEX_DLQ;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INSTITUTION_REPORT_SEARCH_PAGE_SIZE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.PERSISTED_INDEX_DOCUMENT_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;

import java.util.Map;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.EnvironmentFixtures;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.index.apigateway.FetchInstitutionReportHandler;
import no.sikt.nva.nvi.index.apigateway.FetchInstitutionStatusAggregationHandler;
import no.sikt.nva.nvi.index.apigateway.SearchNviCandidatesHandler;

public final class IndexHandlerEnvironments {

  private static final Map<Class<?>, Supplier<FakeEnvironment>> HANDLER_ENVIRONMENTS =
      Map.ofEntries(
          entry(DeletePersistedIndexDocumentHandler.class, EXPANDED_RESOURCES_BUCKET, INDEX_DLQ),
          entry(FetchInstitutionStatusAggregationHandler.class, ALLOWED_ORIGIN),
          entry(
              FetchInstitutionReportHandler.class,
              ALLOWED_ORIGIN,
              COGNITO_HOST,
              INSTITUTION_REPORT_SEARCH_PAGE_SIZE),
          entry(
              IndexDocumentHandler.class,
              EXPANDED_RESOURCES_BUCKET,
              PERSISTED_INDEX_DOCUMENT_QUEUE_URL,
              INDEX_DLQ),
          entry(SearchNviCandidatesHandler.class, ALLOWED_ORIGIN, COGNITO_HOST),
          entry(UpdateIndexHandler.class, EXPANDED_RESOURCES_BUCKET, INDEX_DLQ));

  private IndexHandlerEnvironments() {}

  public static FakeEnvironment forHandler(Class<?> handlerClass) {
    var environmentSupplier = HANDLER_ENVIRONMENTS.get(handlerClass);
    if (isNull(environmentSupplier)) {
      throw new IllegalArgumentException(
          "No test environment defined for " + handlerClass.getSimpleName());
    }
    return environmentSupplier.get();
  }

  private static Map.Entry<Class<?>, Supplier<FakeEnvironment>> entry(
      Class<?> handlerClass, EnvironmentFixtures... environmentVariables) {
    return Map.entry(handlerClass, () -> getHandlerEnvironment(environmentVariables));
  }
}
