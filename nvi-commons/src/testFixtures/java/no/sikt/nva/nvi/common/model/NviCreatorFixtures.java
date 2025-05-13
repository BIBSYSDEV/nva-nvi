package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_UNVERIFIED;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_VERIFIED;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.model.Organization;
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

  public static NviCreator verifiedNviCreatorFrom(Organization... affiliations) {
    var creatorId = randomUriWithSuffix("creatorId");
    return new NviCreator(creatorId, randomString(), STATUS_VERIFIED, List.of(affiliations));
  }

  public static NviCreator unverifiedNviCreatorFrom(Organization... affiliations) {
    return new NviCreator(null, randomString(), STATUS_UNVERIFIED, List.of(affiliations));
  }
}
