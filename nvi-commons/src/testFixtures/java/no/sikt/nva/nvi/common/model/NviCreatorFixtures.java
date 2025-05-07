package no.sikt.nva.nvi.common.model;

import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

public class NviCreatorFixtures {

  public static List<DbCreatorType> mapToDbCreators(
      List<VerifiedNviCreatorDto> verifiedNviCreators,
      List<UnverifiedNviCreatorDto> unverifiedNviCreators) {
    Stream<DbCreatorType> verifiedCreators =
        verifiedNviCreators.stream().map(VerifiedNviCreatorDto::toDao);
    Stream<DbCreatorType> unverifiedCreators =
        unverifiedNviCreators.stream().map(UnverifiedNviCreatorDto::toDao);
    return Stream.concat(verifiedCreators, unverifiedCreators).toList();
  }
}
