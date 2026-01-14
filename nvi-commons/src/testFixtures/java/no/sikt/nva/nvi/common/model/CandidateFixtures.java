package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.defaultExpandedPublicationFactory;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.updateRequestFromPeriod;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateDtoInYear;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;

public class CandidateFixtures {

  public static Candidate setupRandomApplicableCandidate(TestScenario scenario) {
    var candidateRequest = randomUpsertRequestBuilder().build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static UpsertRequestBuilder randomApplicableCandidateRequestBuilder(
      Map<Organization, Collection<NviCreatorDto>> creatorsPerOrganization) {
    return randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, Map<Organization, Collection<NviCreatorDto>> creatorsPerOrganization) {
    var candidateRequest =
        randomUpsertRequestBuilder().withCreatorsAndPoints(creatorsPerOrganization).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, int publicationYear) {
    var publicationDate = randomPublicationDateDtoInYear(publicationYear);
    var candidateRequest =
        randomUpsertRequestBuilder().withPublicationDate(publicationDate).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  public static Candidate setupRandomApplicableCandidate(
      TestScenario scenario, String publicationYear) {
    var publicationDate = randomPublicationDateDtoInYear(publicationYear);
    var candidateRequest =
        randomUpsertRequestBuilder().withPublicationDate(publicationDate).build();
    return scenario.upsertCandidate(candidateRequest);
  }

  /**
   * Sets up a number of candidates in the database for a given year, with a minimal document in S3
   * that matches the publication identifier. Setting up candidates via the service layer requires
   * an open reporting period, so:
   *
   * <ul>
   *   <li>If no reporting period exists for the year, an open period is created
   *   <li>If the reporting period is not open, it is temporarily opened and then reset
   * </ul>
   */
  public static List<Candidate> setupNumberOfCandidatesForYear(
      TestScenario scenario, String year, int candidateCount) {
    var period =
        scenario
            .getPeriodService()
            .findByPublishingYear(year)
            .orElse(setupOpenPeriod(scenario, year));

    if (period.isOpen()) {
      return createCandidatesForYear(scenario, year, candidateCount);
    } else {
      setupOpenPeriod(scenario, year);
      var candidates = createCandidatesForYear(scenario, year, candidateCount);
      scenario.getPeriodService().update(updateRequestFromPeriod(period));
      return candidates;
    }
  }

  private static List<Candidate> createCandidatesForYear(
      TestScenario scenario, String year, int candidateCount) {
    var candidates =
        Stream.generate(() -> setupRandomApplicableCandidate(scenario, year))
            .limit(candidateCount)
            .toList();
    candidates.forEach(candidate -> createMatchingPublicationInS3(scenario, candidate));
    return candidates;
  }

  private static void createMatchingPublicationInS3(TestScenario scenario, Candidate candidate) {
    var publication =
        defaultExpandedPublicationFactory(scenario)
            .getExpandedPublicationBuilder()
            .withId(candidate.getPublicationId())
            .withIdentifier(candidate.publicationDetails().publicationIdentifier())
            .withTitle(candidate.publicationDetails().title())
            .build();
    scenario.setupExpandedPublicationInS3(publication);
  }
}
