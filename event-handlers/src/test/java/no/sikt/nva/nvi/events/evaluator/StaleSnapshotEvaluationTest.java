package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder.randomPublicationDetailsDtoBuilder;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evaluations of the same publication may run in parallel and read whatever version of the
 * persisted resource is current, so an older snapshot may be evaluated after a newer one. See <a
 * href="https://sikt.atlassian.net/browse/NP-51602">NP-51602</a>.
 */
class StaleSnapshotEvaluationTest extends EvaluationTest {

  private static final String STATUS_DRAFT = "DRAFT";
  private static final Instant LATER_MODIFIED_DATE = Instant.now();
  private static final Instant EARLIER_MODIFIED_DATE = LATER_MODIFIED_DATE.minusSeconds(10);

  private SampleExpandedPublicationFactory factory;
  private URI publicationId;

  @BeforeEach
  void setup() {
    setupOpenPeriod(scenario, CURRENT_YEAR);
    factory = new SampleExpandedPublicationFactory();
    publicationId = factory.getPublicationId();
    var nviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    factory.withCreatorAffiliatedWith(nviOrganization);
    mockGetAllCustomersResponse(factory.getCustomerOrganizations());
  }

  @Test
  void shouldKeepCandidateWhenEvaluatingSnapshotOlderThanStoredCandidate() {
    handleEvaluation(snapshot("PUBLISHED", LATER_MODIFIED_DATE));

    handleEvaluation(snapshot(STATUS_DRAFT, EARLIER_MODIFIED_DATE));

    assertThatPublicationIsValidCandidate(publicationId);
    var candidate = candidateService.getCandidateByPublicationId(publicationId);
    assertThat(candidate.approvals()).isNotEmpty();
    assertThat(candidate.publicationDetails().modifiedDate()).isEqualTo(LATER_MODIFIED_DATE);
  }

  @Test
  void shouldEvaluateSnapshotNewerThanStoredCandidate() {
    handleEvaluation(snapshot("PUBLISHED", EARLIER_MODIFIED_DATE));

    handleEvaluation(snapshot(STATUS_DRAFT, LATER_MODIFIED_DATE));

    assertThatPublicationIsNonCandidate(publicationId);
  }

  @Test
  void shouldEvaluateSnapshotWithSameModifiedDateAsStoredCandidate() {
    handleEvaluation(snapshot("PUBLISHED", LATER_MODIFIED_DATE));

    handleEvaluation(snapshot(STATUS_DRAFT, LATER_MODIFIED_DATE));

    assertThatPublicationIsNonCandidate(publicationId);
  }

  @Test
  void shouldEvaluateSnapshotWhenStoredCandidateHasNoModifiedDate() {
    setupCandidateWithoutModifiedDate();

    handleEvaluation(snapshot(STATUS_DRAFT, EARLIER_MODIFIED_DATE));

    assertThatPublicationIsNonCandidate(publicationId);
  }

  private void setupCandidateWithoutModifiedDate() {
    var request =
        randomUpsertRequestBuilder()
            .withPublicationDetails(
                randomPublicationDetailsDtoBuilder().withModifiedDate(null).build())
            .withPublicationId(publicationId)
            .build();
    scenario.upsertCandidate(request);
  }

  private String snapshot(String status, Instant modifiedDate) {
    return factory
        .getExpandedPublicationBuilder()
        .withStatus(status)
        .withModifiedDate(modifiedDate.toString())
        .build()
        .toJsonString();
  }
}
