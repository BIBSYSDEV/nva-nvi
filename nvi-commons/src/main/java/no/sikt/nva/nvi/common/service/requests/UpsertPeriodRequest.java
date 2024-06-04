package no.sikt.nva.nvi.common.service.requests;

import java.time.Instant;

public interface UpsertPeriodRequest {

    Integer publishingYear();

    Instant startDate();

    Instant reportingDate();

    void validate() throws IllegalArgumentException;
}
