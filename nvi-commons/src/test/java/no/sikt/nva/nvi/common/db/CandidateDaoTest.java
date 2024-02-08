package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

class CandidateDaoTest {

    @Test
    void shouldRoundTripCandidateDaoWithoutLossOfData() throws JsonProcessingException {
        var dao = CandidateDao.builder()
                      .candidate(randomCandidate())
                      .identifier(UUID.randomUUID())
                      .periodYear(randomString())
                      .version(randomString())
                      .build();

        var json = dao.toString();
        var roundTrippedDao = JsonUtils.dtoObjectMapper.readValue(json, CandidateDao.class);

        assertEquals(dao, roundTrippedDao);
    }
}