package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.COGNITO_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INDEX_DLQ;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INSTITUTION_REPORT_SEARCH_PAGE_SIZE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.NVI_REPORTS_BUCKET;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.PERSISTED_INDEX_DOCUMENT_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.REPORT_QUEUE;
import static no.sikt.nva.nvi.common.HandlerEnvironments.entry;

import java.util.Map;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.HandlerEnvironments;
import no.sikt.nva.nvi.index.apigateway.FetchInstitutionReportHandler;
import no.sikt.nva.nvi.index.apigateway.FetchInstitutionStatusAggregationHandler;
import no.sikt.nva.nvi.index.apigateway.SearchNviCandidatesHandler;
import no.sikt.nva.nvi.index.report.FetchReportHandler;
import no.sikt.nva.nvi.index.report.GenerateReportHandler;

/**
 * Fake environment variables for each handler in this module. Keep this in sync with the actual
 * environment variables defined in template.yaml.
 */
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
          entry(FetchReportHandler.class, ALLOWED_ORIGIN, NVI_REPORTS_BUCKET, REPORT_QUEUE),
          entry(GenerateReportHandler.class, NVI_REPORTS_BUCKET),
          entry(
              IndexDocumentHandler.class,
              EXPANDED_RESOURCES_BUCKET,
              PERSISTED_INDEX_DOCUMENT_QUEUE_URL,
              INDEX_DLQ),
          entry(SearchNviCandidatesHandler.class, ALLOWED_ORIGIN, COGNITO_HOST),
          entry(UpdateIndexHandler.class, EXPANDED_RESOURCES_BUCKET, INDEX_DLQ));

  private IndexHandlerEnvironments() {}

  public static FakeEnvironment forHandler(Class<?> handlerClass) {
    return HandlerEnvironments.forHandler(HANDLER_ENVIRONMENTS, handlerClass);
  }
}
