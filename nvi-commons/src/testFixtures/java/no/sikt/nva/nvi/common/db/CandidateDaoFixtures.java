package no.sikt.nva.nvi.common.db;

import static java.util.Objects.nonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.test.TestUtils;

public class CandidateDaoFixtures {
    private static final String UUID_SEPARATOR = "-";

    public static List<CandidateDao> createNumberOfCandidatesForYear(
        String year, int number, CandidateRepository repository) {
      return IntStream
                 .range(0, number)
                 .mapToObj(i -> TestUtils.randomCandidateWithYear(year))
                 .map(candidate -> createCandidateDao(repository, candidate))
                 .toList();
    }

    public static CandidateDao createCandidateDao(
        CandidateRepository repository, DbCandidate candidate) {
      return repository.create(candidate, List.of());
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

    public static Map<String, String> getYearIndexStartMarker(CandidateDao dao) {
      return Map.of(
          "PrimaryKeyRangeKey", dao.primaryKeyRangeKey(),
          "PrimaryKeyHashKey", dao.primaryKeyHashKey(),
          "SearchByYearHashKey", String.valueOf(dao.searchByYearHashKey()),
          "SearchByYearRangeKey", String.valueOf(dao.searchByYearSortKey()));
    }
}
