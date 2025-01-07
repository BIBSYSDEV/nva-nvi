package no.sikt.nva.nvi.common.service.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbUnverifiedCreator;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NviCreatorDtoTest {

    private static final String CREATOR_NAME = randomString();
    private static final URI CREATOR_ID = randomUri();
    private static final List<URI> CREATOR_AFFILIATIONS = List.of(randomUri(), randomUri());
    private static final VerifiedNviCreatorDto DEFAULT_VERIFIED_CREATOR = new VerifiedNviCreatorDto(CREATOR_ID,
                                                                                                    CREATOR_AFFILIATIONS);
    private static final UnverifiedNviCreatorDto DEFAULT_UNVERIFIED_CREATOR = new UnverifiedNviCreatorDto(CREATOR_NAME,
                                                                                                          CREATOR_AFFILIATIONS);

    static Stream<NviCreatorDto> creatorTypeProvider() {
        return Stream.of(DEFAULT_VERIFIED_CREATOR, DEFAULT_UNVERIFIED_CREATOR);
    }

    @Test
    void builderShouldCreateNewVerifiedCreator() {
        var creator = VerifiedNviCreatorDto.builder()
                                           .withId(CREATOR_ID)
                                           .withAffiliations(CREATOR_AFFILIATIONS)
                                           .build();
        assertEquals(DEFAULT_VERIFIED_CREATOR, creator);
    }

    @Test
    void builderShouldCreateUnverifiedCreator() {
        var creator = UnverifiedNviCreatorDto.builder()
                                             .withName(CREATOR_NAME)
                                             .withAffiliations(CREATOR_AFFILIATIONS)
                                             .build();
        assertEquals(DEFAULT_UNVERIFIED_CREATOR, creator);
    }

    @ParameterizedTest(name = "Should convert {0} to and from dao")
    @MethodSource("creatorTypeProvider")
    void shouldConvertCreatorToAndFromDao(NviCreatorDto creator) {
        var roundTrippedCreator = creator.toDao()
                                                 .copy()
                                                 .toNviCreatorType();
        assertEquals(creator, roundTrippedCreator);
    }

    @Test
    void shouldGetSameResultWithToDaoAsBuilder() {
        var dbCreator1 = DbUnverifiedCreator.builder()
                                            .creatorName(CREATOR_NAME)
                                            .affiliations(CREATOR_AFFILIATIONS)
                                            .build();
        var dbCreator2 = DEFAULT_UNVERIFIED_CREATOR.toDao();
        assertEquals(dbCreator1, dbCreator2);
    }
}