package no.sikt.nva.nvi.common.db;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.common.SampleExpandedPublicationFactory.defaultExpandedPublicationFactory;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApprovalDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateWithYear;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import nva.commons.core.paths.UriWrapper;

public class CandidateDaoFixtures {
  private static final String UUID_SEPARATOR = "-";

  public static CandidateDao randomApplicableCandidateDao() {
    return CandidateDao.builder()
        .identifier(randomUUID())
        .candidate(randomCandidate())
        .version(randomUUID().toString())
        .periodYear(randomYear())
        .build();
  }

  public static List<CandidateDao> createNumberOfCandidatesForYear(
      String year, int number, TestScenario scenario) {
    setupOpenPeriod(scenario, year);
    var candidates =
        IntStream.range(0, number)
            .mapToObj(i -> randomCandidateWithYear(randomOrganizationId(), year))
            .map(CandidateDaoFixtures::createCandidateDao)
            .toList();

    var candidateRepository = scenario.getCandidateRepository();
    for (var candidate : candidates) {
      candidateRepository.create(candidate, emptyList());
      createMatchingPublicationInS3(candidate, scenario);
    }
    return candidates;
  }

  private static void createMatchingPublicationInS3(
      CandidateDao candidateDao, TestScenario scenario) {
    var publication =
        defaultExpandedPublicationFactory(scenario)
            .getExpandedPublicationBuilder()
            .withId(candidateDao.candidate().publicationId())
            .withIdentifier(UUID.fromString(candidateDao.candidate().publicationIdentifier()))
            .build();
    scenario.setupExpandedPublicationInS3(publication);
  }

  public static UUID createCandidateInRepository(
      CandidateRepository repository, DbCandidate candidate) {
    var candidateDao = createCandidateDao(candidate);
    repository.create(candidateDao, emptyList());
    return candidateDao.identifier();
  }

  public static CandidateDao createCandidateDao(DbCandidate candidate) {
    var periodYear = candidate.getPublicationDate().year();
    return CandidateDao.builder()
        .identifier(randomUUID())
        .candidate(candidate)
        .periodYear(periodYear)
        .build();
  }

  public static List<CandidateDao> sortByIdentifier(List<CandidateDao> candidates, Integer limit) {
    var comparator = Comparator.comparing(CandidateDaoFixtures::getCharacterValues);
    return candidates.stream()
        .sorted(Comparator.comparing(CandidateDao::identifier, comparator))
        .limit(nonNull(limit) ? limit : candidates.size())
        .toList();
  }

  private static String getCharacterValues(UUID uuid) {
    return uuid.toString().replaceAll(UUID_SEPARATOR, "");
  }

  /*
   * This method is used to set up a reported candidate, but should be removed once Candidate.report is implemented.
   */
  @Deprecated
  public static CandidateDao setupReportedCandidate(CandidateRepository repository, String year) {
    return setupReportedCandidate(repository, year, randomOrganizationId());
  }

  /*
   * This method is used to set up a reported candidate, but should be removed once Candidate.report is implemented.
   */
  @Deprecated
  public static CandidateDao setupReportedCandidate(
      CandidateRepository repository, String year, URI organizationId) {
    var dbCandidate =
        randomCandidateWithYear(organizationId, year)
            .copy()
            .reportStatus(ReportStatus.REPORTED)
            .build();
    var candidateDao = createCandidateDao(dbCandidate);
    var approvals = List.of(randomApprovalDao(candidateDao.identifier(), organizationId));
    repository.create(candidateDao, approvals);
    return repository.findCandidateById(candidateDao.identifier()).orElseThrow();
  }

  public static Map<String, String> getYearIndexStartMarker(CandidateDao dao) {
    return Map.of(
        "PrimaryKeyRangeKey", dao.primaryKeyRangeKey(),
        "PrimaryKeyHashKey", dao.primaryKeyHashKey(),
        "SearchByYearHashKey", String.valueOf(dao.searchByYearHashKey()),
        "SearchByYearRangeKey", String.valueOf(dao.searchByYearSortKey()));
  }

  public static URI getExpectedPublicationBucketUri(String identifier) {
    return UriWrapper.fromUri("s3://" + EXPANDED_RESOURCES_BUCKET.getValue())
        .addChild("resources")
        .addChild(identifier + ".gz")
        .getUri();
  }
}
