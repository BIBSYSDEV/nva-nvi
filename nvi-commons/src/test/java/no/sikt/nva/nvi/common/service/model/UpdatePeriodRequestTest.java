package no.sikt.nva.nvi.common.service.model;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdatePeriodRequestTest {

    Builder builder;

    @BeforeEach
    void setUp() {
        builder = UpdatePeriodRequest.builder()
                                     .withPublishingYear(1999)
                                     .withStartDate(Instant.now().plus(1, DAYS))
                                     .withReportingDate(Instant.now().plus(2, DAYS))
                                     .withModifiedBy(new Username("username"));
    }

    @Test
    void shouldNotThrowIfRequestIsValid() {
        var request = builder.build();
        assertDoesNotThrow(request::validate);
    }

    @Test
    void shouldThrowIfPublishingYearIsNull() {
        var request = builder.withPublishingYear(null).build();
        assertThrows(IllegalArgumentException.class, request::validate);
    }

    @Test
    void shouldThrowIfStartDateIsNull() {
        var request = builder.withStartDate(null).build();
        assertThrows(IllegalArgumentException.class, request::validate);
    }

    @Test
    void shouldThrowIfReportingDateIsNull() {
        var request = builder.withReportingDate(null).build();
        assertThrows(IllegalArgumentException.class, request::validate);
    }

    @Test
    void shouldThrowIfModifiedByIsNull() {
        var request = builder.withModifiedBy(null).build();
        assertThrows(IllegalArgumentException.class, request::validate);
    }
}
