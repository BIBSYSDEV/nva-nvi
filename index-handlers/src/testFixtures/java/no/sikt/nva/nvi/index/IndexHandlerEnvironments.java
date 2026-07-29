package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.COGNITO_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INDEX_DLQ;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.INSTITUTION_REPORT_SEARCH_PAGE_SIZE;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.PERSISTED_INDEX_DOCUMENT_QUEUE_URL;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;

import no.sikt.nva.nvi.common.FakeEnvironment;

public final class IndexHandlerEnvironments {

  private IndexHandlerEnvironments() {}

  public static FakeEnvironment getDeletePersistedIndexDocumentHandlerEnvironment() {
    return getHandlerEnvironment(EXPANDED_RESOURCES_BUCKET, INDEX_DLQ);
  }

  public static FakeEnvironment getFetchInstitutionStatusAggregationHandlerEnvironment() {
    return getHandlerEnvironment(ALLOWED_ORIGIN);
  }

  public static FakeEnvironment getFetchInstitutionReportHandlerEnvironment() {
    return getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST, INSTITUTION_REPORT_SEARCH_PAGE_SIZE);
  }

  public static FakeEnvironment getIndexDocumentHandlerEnvironment() {
    return getHandlerEnvironment(
        EXPANDED_RESOURCES_BUCKET, PERSISTED_INDEX_DOCUMENT_QUEUE_URL, INDEX_DLQ);
  }

  public static FakeEnvironment getSearchNviCandidatesHandlerEnvironment() {
    return getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST);
  }

  public static FakeEnvironment getUpdateIndexHandlerEnvironment() {
    return getHandlerEnvironment(EXPANDED_RESOURCES_BUCKET, INDEX_DLQ);
  }
}
