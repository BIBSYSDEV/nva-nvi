package no.sikt.nva.nvi.events.evaluator;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.CustomerDtoFixtures.createNviCustomer;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluateNviCandidateWithCristinDataTest extends EvaluationTest {

  private static final URI BASE_PATH =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization");
  private static final URI NTNU_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("194.0.0.0").getUri();
  private static final URI ST_OLAVS_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("1920.0.0.0").getUri();
  private static final URI UIO_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("185.90.0.0").getUri();
  private static final URI SINTEF_TOP_LEVEL_ORG_ID =
      UriWrapper.fromUri(BASE_PATH).addChild("7401.0.0.0").getUri();
  private static final UUID PUBLICATION_IDENTIFIER = randomUUID();
  private static final URI PUBLICATION_ID = generatePublicationId(PUBLICATION_IDENTIFIER);

  @BeforeEach
  void setup() {
    mockCustomerApi();
    setupOpenPeriod(scenario, "2022");
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicArticleFrom2022() {
    var path = "evaluator/cristin_candidate_2022_academicArticle.json";
    var publication = getPublicationFromFile(path, PUBLICATION_ID);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(PUBLICATION_ID);
    assertThat(getPointsForInstitution(candidate, NTNU_TOP_LEVEL_ORG_ID))
        .isEqualTo(scaledBigDecimal(0.8165));
    assertThat(getPointsForInstitution(candidate, ST_OLAVS_TOP_LEVEL_ORG_ID))
        .isEqualTo(scaledBigDecimal(0.5774));
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicMonographFrom2022() {
    var path = "evaluator/cristin_candidate_2022_academicMonograph.json";
    var publication = getPublicationFromFile(path, PUBLICATION_ID);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(PUBLICATION_ID);
    assertThat(getPointsForInstitution(candidate, UIO_TOP_LEVEL_ORG_ID))
        .isEqualTo(scaledBigDecimal(3.7528));
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForLiteratureReviewFrom2022() {
    var path = "evaluator/cristin_candidate_2022_academicLiteratureReview.json";
    var publication = getPublicationFromFile(path, PUBLICATION_ID);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(PUBLICATION_ID);
    assertThat(getPointsForInstitution(candidate, NTNU_TOP_LEVEL_ORG_ID))
        .isEqualTo(scaledBigDecimal(1.5922));
  }

  @Test
  void shouldReturnSamePointsAsPointsCalculatedByCristinForAcademicChapterFrom2022() {
    var path = "evaluator/cristin_candidate_2022_academicChapter.json";
    var publication = getPublicationFromFile(path, PUBLICATION_ID);

    handleEvaluation(publication);

    var candidate = candidateService.getCandidateByPublicationId(PUBLICATION_ID);
    assertThat(getPointsForInstitution(candidate, NTNU_TOP_LEVEL_ORG_ID))
        .isEqualTo(scaledBigDecimal(0.8660));
    assertThat(getPointsForInstitution(candidate, SINTEF_TOP_LEVEL_ORG_ID))
        .isEqualTo(scaledBigDecimal(0.5000));
  }

  private BigDecimal getPointsForInstitution(Candidate candidate, URI institutionId) {
    return candidate
        .getInstitutionPoints(institutionId)
        .map(InstitutionPoints::institutionPoints)
        .orElseThrow();
  }

  private static BigDecimal scaledBigDecimal(double val) {
    return BigDecimal.valueOf(val).setScale(SCALE, ROUNDING_MODE);
  }

  private void mockCustomerApi() {
    var customers =
        List.of(
            createNviCustomer(NTNU_TOP_LEVEL_ORG_ID),
            createNviCustomer(ST_OLAVS_TOP_LEVEL_ORG_ID),
            createNviCustomer(UIO_TOP_LEVEL_ORG_ID),
            createNviCustomer(SINTEF_TOP_LEVEL_ORG_ID));
    mockGetAllCustomersResponse(customers);
  }
}
