package no.sikt.nva.nvi.common.service;

import static java.time.temporal.ChronoUnit.DAYS;
import static no.sikt.nva.nvi.test.TestUtils.randomUserName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NviPeriodServiceTest extends LocalDynamoTest {

    private PeriodRepository periodRepository;
    private NviPeriodService nviPeriodService;

    @BeforeEach
    void setup() {
        periodRepository = new PeriodRepository(initializeTestDatabase());
        nviPeriodService = new NviPeriodService(periodRepository);
    }

    @Test
    void shouldReturnPeriodsOnlyWhenFetchingPeriods() {
        NviPeriod.create(createRequest(2022, Instant.now().plus(1, DAYS), Instant.now().plus(365, DAYS)),
                         periodRepository);
        NviPeriod.create(createRequest(2023, Instant.now().plus(1, DAYS), Instant.now().plus(365, DAYS)),
                         periodRepository);
        assertEquals(2, nviPeriodService.fetchAll().size());
    }

    private static CreatePeriodRequest createRequest(int year, Instant startDate, Instant reportingDate) {
        return CreatePeriodRequest.builder()
                   .withPublishingYear(year)
                   .withStartDate(startDate)
                   .withReportingDate(reportingDate)
                   .withCreatedBy(randomUserName())
                   .build();
    }
}