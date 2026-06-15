package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

class CandidateTest {

  @Test
  void shouldMakeRoundTripWithoutLossOfInformation() throws JsonProcessingException {
    var candidate = randomCandidate();
    var json = JsonUtils.dtoObjectMapper.writeValueAsString(candidate);
    var reconstructedCandidate = JsonUtils.dtoObjectMapper.readValue(json, DbCandidate.class);
    assertEquals(candidate, reconstructedCandidate);
  }
}
