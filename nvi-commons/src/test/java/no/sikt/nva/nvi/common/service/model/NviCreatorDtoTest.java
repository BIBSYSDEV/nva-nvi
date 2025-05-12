package no.sikt.nva.nvi.common.service.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import org.junit.jupiter.api.Test;

class NviCreatorDtoTest {

  private static final String CREATOR_NAME = randomString();
  private static final URI CREATOR_ID = randomUri();
  private static final List<URI> CREATOR_AFFILIATIONS = List.of(randomUri(), randomUri());
  private static final VerifiedNviCreatorDto DEFAULT_VERIFIED_CREATOR =
      new VerifiedNviCreatorDto(CREATOR_ID, null, CREATOR_AFFILIATIONS);
  private static final UnverifiedNviCreatorDto DEFAULT_UNVERIFIED_CREATOR =
      new UnverifiedNviCreatorDto(CREATOR_NAME, CREATOR_AFFILIATIONS);

  @Test
  void builderShouldCreateNewVerifiedCreator() {
    var creator =
        VerifiedNviCreatorDto.builder()
            .withId(CREATOR_ID)
            .withAffiliations(CREATOR_AFFILIATIONS)
            .build();
    assertEquals(DEFAULT_VERIFIED_CREATOR, creator);
  }

  @Test
  void builderShouldCreateUnverifiedCreator() {
    var creator =
        UnverifiedNviCreatorDto.builder()
            .withName(CREATOR_NAME)
            .withAffiliations(CREATOR_AFFILIATIONS)
            .build();
    assertEquals(DEFAULT_UNVERIFIED_CREATOR, creator);
  }

  @Test
  void shouldConvertVerifiedCreatorToAndFromDao() {
    var roundTrippedCreator = DEFAULT_VERIFIED_CREATOR.toDao().copy().toNviCreator();
    assertEquals(DEFAULT_VERIFIED_CREATOR, roundTrippedCreator);
  }

  @Test
  void shouldConvertUnverifiedCreatorToAndFromDao() {
    var roundTrippedCreator = DEFAULT_UNVERIFIED_CREATOR.toDao().copy().toNviCreator();
    assertEquals(DEFAULT_UNVERIFIED_CREATOR, roundTrippedCreator);
  }

  @Test
  void shouldGetSameResultWithToDaoAsBuilder() {
    var dbCreator1 =
        DbUnverifiedCreator.builder()
            .creatorName(CREATOR_NAME)
            .affiliations(CREATOR_AFFILIATIONS)
            .build();
    var dbCreator2 = DEFAULT_UNVERIFIED_CREATOR.toDao();
    assertEquals(dbCreator1, dbCreator2);
  }
}
