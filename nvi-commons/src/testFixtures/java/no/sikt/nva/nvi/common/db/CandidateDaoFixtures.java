package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;

public class CandidateDaoFixtures {
  private static final String UUID_SEPARATOR = "-";

  public static CandidateDao randomApplicableCandidateDao() {
    return new CandidateDao(
        UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString(), randomYear());
  }

  public static CandidateDao randomNonApplicableCandidateDao() {
    return new CandidateDao(
        UUID.randomUUID(), randomCandidateBuilder(false).build(),
        UUID.randomUUID().toString(), randomString());
  }

  public static List<CandidateDao> createNumberOfCandidatesForYear(
      String year, int number, CandidateRepository repository) {
    return IntStream.range(0, number)
        .mapToObj(i -> DbCandidateFixtures.randomCandidateWithYear(year))
        .map(candidate -> createCandidateDao(repository, candidate))
        .toList();
  }

  public static CandidateDao createCandidateDao(
      CandidateRepository repository, DbCandidate candidate) {
    return repository.create(candidate, List.of());
  }

  public static CandidateDao createCandidateDao(DbCandidate candidate) {
    return CandidateDao.builder().identifier(UUID.randomUUID()).candidate(candidate).build();
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
    var institutionId = randomUri();
    return repository.create(
        randomCandidateBuilder(true, institutionId)
            .publicationDate(DbPublicationDate.builder().year(year).build())
            .reportStatus(ReportStatus.REPORTED)
            .build(),
        List.of(DbApprovalStatusFixtures.randomApproval(institutionId)));
  }

  /*
   * This method is used to set up a reported candidate, but should be removed once Candidate.report is implemented.
   */
  @Deprecated
  public static CandidateDao setupReportedCandidate(
      CandidateRepository repository, String year, URI organizationId) {
    return repository.create(
        randomCandidateBuilder(true, organizationId)
            .publicationDate(DbPublicationDate.builder().year(year).build())
            .reportStatus(ReportStatus.REPORTED)
            .build(),
        List.of(DbApprovalStatusFixtures.randomApproval(organizationId)));
  }

  public static Map<String, String> getYearIndexStartMarker(CandidateDao dao) {
    return Map.of(
        "PrimaryKeyRangeKey", dao.primaryKeyRangeKey(),
        "PrimaryKeyHashKey", dao.primaryKeyHashKey(),
        "SearchByYearHashKey", String.valueOf(dao.searchByYearHashKey()),
        "SearchByYearRangeKey", String.valueOf(dao.searchByYearSortKey()));
  }
}
