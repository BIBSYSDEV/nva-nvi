package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceType;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

class CandidateTest {

    @Test
    void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
        var candidate = randomCandidate();
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(candidate);
        var reconstructedCandidate = JsonUtils.dtoObjectMapper.readValue(json, DbCandidate.class);
        assertThat(reconstructedCandidate, is(equalTo(candidate)));
    }

    private static List<DbInstitutionPoints> getExpectedInstitutionPoints(List<DbCreator> creators) {
        return creators.stream()
                   .flatMap(creator -> creator.affiliations()
                                           .stream()
                                           .map(affiliation -> mapToInstitutionPoints(creator, affiliation)))
                   .toList();
    }

    private static DbInstitutionPoints mapToInstitutionPoints(DbCreator creator, URI affiliation) {
        return new DbInstitutionPoints(affiliation, randomBigDecimal(),
                                       List.of(new DbCreatorAffiliationPoints(creator.creatorId(),
                                                                              affiliation,
                                                                              randomBigDecimal())));
    }

    private DbCandidate randomCandidate() {
        var creators = randomVerifiedCreators();
        var institutionPoints = getExpectedInstitutionPoints(creators);
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .creatorCount(randomInteger())
                   .instanceType(randomInstanceType().getValue())
                   .level(DbLevel.LEVEL_ONE)
                   .applicable(true)
                   .internationalCollaboration(true)
                   .creators(creators)
                   .publicationDate(localDateNowAsPublicationDate())
                   .points(institutionPoints)
                   .createdDate(Instant.now())
                   .modifiedDate(Instant.now())
                   .build();
    }

    private DbPublicationDate localDateNowAsPublicationDate() {
        var now = LocalDate.now();
        return new DbPublicationDate(String.valueOf(now.getYear()), String.valueOf(now.getMonth()),
                                     String.valueOf(now.getDayOfMonth()));
    }

    private List<DbCreator> randomVerifiedCreators() {
        return IntStream.range(1, 20).boxed().map(i -> randomVerifiedCreator()).toList();
    }

    private DbCreator randomVerifiedCreator() {
        return DbCreator.builder()
                   .creatorId(randomUri())
                   .affiliations(randomAffiliations())
                   .build();
    }

    private List<URI> randomAffiliations() {
        return IntStream.range(1, 20).boxed().map(i -> randomUri()).toList();
    }
}
