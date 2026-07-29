package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.COGNITO_HOST;
import static no.sikt.nva.nvi.common.HandlerEnvironments.entry;

import java.util.Map;
import java.util.function.Supplier;
import no.sikt.nva.nvi.common.FakeEnvironment;
import no.sikt.nva.nvi.common.HandlerEnvironments;
import no.sikt.nva.nvi.rest.create.CreateNoteHandler;
import no.sikt.nva.nvi.rest.create.CreateNviPeriodHandler;
import no.sikt.nva.nvi.rest.fetch.FetchNviCandidateByPublicationIdHandler;
import no.sikt.nva.nvi.rest.fetch.FetchNviCandidateContextHandler;
import no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler;
import no.sikt.nva.nvi.rest.fetch.FetchNviPeriodHandler;
import no.sikt.nva.nvi.rest.fetch.FetchNviPeriodsHandler;
import no.sikt.nva.nvi.rest.fetch.FetchReportStatusByPublicationIdHandler;
import no.sikt.nva.nvi.rest.remove.RemoveNoteHandler;
import no.sikt.nva.nvi.rest.upsert.UpdateNviCandidateStatusHandler;
import no.sikt.nva.nvi.rest.upsert.UpdateNviPeriodHandler;
import no.sikt.nva.nvi.rest.upsert.UpsertAssigneeHandler;

/**
 * Fake environment variables for each handler in this module. Keep this in sync with the actual
 * environment variables defined in template.yaml.
 */
public final class RestHandlerEnvironments {

  private static final Map<Class<?>, Supplier<FakeEnvironment>> HANDLER_ENVIRONMENTS =
      Map.ofEntries(
          entry(CreateNoteHandler.class, ALLOWED_ORIGIN, COGNITO_HOST),
          entry(CreateNviPeriodHandler.class, ALLOWED_ORIGIN),
          entry(FetchNviCandidateByPublicationIdHandler.class, ALLOWED_ORIGIN),
          entry(FetchNviCandidateContextHandler.class, ALLOWED_ORIGIN),
          entry(FetchNviCandidateHandler.class, ALLOWED_ORIGIN, COGNITO_HOST),
          entry(FetchNviPeriodHandler.class, ALLOWED_ORIGIN),
          entry(FetchNviPeriodsHandler.class, ALLOWED_ORIGIN),
          entry(FetchReportStatusByPublicationIdHandler.class, ALLOWED_ORIGIN),
          entry(RemoveNoteHandler.class, ALLOWED_ORIGIN, COGNITO_HOST),
          entry(UpdateNviCandidateStatusHandler.class, ALLOWED_ORIGIN, COGNITO_HOST),
          entry(UpdateNviPeriodHandler.class, ALLOWED_ORIGIN),
          entry(UpsertAssigneeHandler.class, ALLOWED_ORIGIN, COGNITO_HOST));

  private RestHandlerEnvironments() {}

  public static FakeEnvironment forHandler(Class<?> handlerClass) {
    return HandlerEnvironments.forHandler(HANDLER_ENVIRONMENTS, handlerClass);
  }
}
