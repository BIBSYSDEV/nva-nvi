package no.sikt.nva.nvi.rest;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.ALLOWED_ORIGIN;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.COGNITO_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getHandlerEnvironment;

import no.sikt.nva.nvi.common.FakeEnvironment;

/**
 * Fake environment variables for each handler in this module. Keep this in sync with the actual
 * environment variables defined in template.yaml.
 */
public class EnvironmentFixtures {

  public static final FakeEnvironment FETCH_NVI_CANDIDATE_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST);

  public static final FakeEnvironment FETCH_NVI_CANDIDATE_BY_PUBLICATION_ID_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);

  public static final FakeEnvironment FETCH_REPORT_STATUS_BY_PUBLICATION_ID_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);

  public static final FakeEnvironment UPDATE_NVI_CANDIDATE_STATUS_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST);

  public static final FakeEnvironment UPSERT_ASSIGNEE_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST);

  public static final FakeEnvironment CREATE_NOTE_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST);

  public static final FakeEnvironment REMOVE_NOTE_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN, COGNITO_HOST);

  public static final FakeEnvironment CREATE_NVI_PERIOD_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);

  public static final FakeEnvironment FETCH_NVI_PERIOD_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);

  public static final FakeEnvironment FETCH_NVI_PERIODS_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);

  public static final FakeEnvironment UPDATE_NVI_PERIOD_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);

  public static final FakeEnvironment FETCH_NVI_CANDIDATE_CONTEXT_HANDLER =
      getHandlerEnvironment(ALLOWED_ORIGIN);
}
