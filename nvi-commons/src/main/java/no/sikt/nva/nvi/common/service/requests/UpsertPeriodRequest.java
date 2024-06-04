package no.sikt.nva.nvi.common.service.requests;

import java.time.Instant;
import no.sikt.nva.nvi.common.service.model.Username;

public interface UpsertPeriodRequest {

    Integer publishingYear();

    Instant startDate();

    Instant reportingDate();

    Username createdBy();

    void validate() throws IllegalArgumentException;
}
