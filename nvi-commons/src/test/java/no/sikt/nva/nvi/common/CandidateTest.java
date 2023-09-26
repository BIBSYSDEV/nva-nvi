package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceType;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.model.CandidateDao.Creator;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstitutionPoints;
import no.sikt.nva.nvi.common.db.model.CandidateDao.ChannelLevel;
import no.sikt.nva.nvi.common.db.model.CandidateDao.PublicationDate;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

public class CandidateTest {

    @Test
    void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
        var candidate = randomCandidate();
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(candidate);
        var reconstructedCandidate = JsonUtils.dtoObjectMapper.readValue(json, CandidateData.class);
        assertThat(reconstructedCandidate, is(equalTo(candidate)));
    }

    private CandidateData randomCandidate() {
        return CandidateData.builder()
                   .publicationId(randomUri())
                   .creatorCount(randomInteger())
                   .instanceType(randomInstanceType())
                   .level(ChannelLevel.LEVEL_ONE)
                   .applicable(true)
                   .internationalCollaboration(true)
                   .creators(randomVerifiedCreators())
                   .publicationDate(localDateNowAsPublicationDate())
                   .points(List.of(new InstitutionPoints(randomUri(), randomBigDecimal())))
                   .build();
    }

    private PublicationDate localDateNowAsPublicationDate() {
        var now = LocalDate.now();
        return new PublicationDate(String.valueOf(now.getYear()), String.valueOf(now.getMonth()),
                                   String.valueOf(now.getDayOfMonth()));
    }

    private List<Creator> randomVerifiedCreators() {
        return IntStream.range(1, 20).boxed().map(i -> randomVerifiedCreator()).toList();
    }

    private Creator randomVerifiedCreator() {
        return new Creator(randomUri(), randomAffiliations());
    }

    private List<URI> randomAffiliations() {
        return IntStream.range(1, 20).boxed().map(i -> randomUri()).toList();
    }
}
