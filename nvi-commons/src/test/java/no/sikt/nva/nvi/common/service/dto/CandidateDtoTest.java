package no.sikt.nva.nvi.common.service.dto;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.unit.nva.commons.json.JsonUtils;
import org.junit.jupiter.api.Test;

class CandidateDtoTest {

    //TODO: To remove when frontend has been updated to use "period" property
    @Deprecated
    @Test
    void shouldDeserializeCandidateDtoContainingPeriodAndPeriodStatusProperties() throws JsonProcessingException {
        var candidate = CandidateDto.builder()
                            .withPeriod(PeriodStatusDto.builder().withId(randomUri()).build())
                            .build();

        var node = JsonUtils.dtoObjectMapper.readTree(candidate.toJsonString());

        assertTrue(node.at("/period").isObject());
        assertTrue(node.at("/periodStatus").isObject());
    }
}