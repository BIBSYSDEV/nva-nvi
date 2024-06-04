package no.sikt.nva.nvi.common.service.model;

import java.time.Instant;

public class CreatePeriodRequest extends UpsertPeriodRequest {

    public CreatePeriodRequest(Integer publishingYear, Instant startDate, Instant reportingDate, Username createdBy) {
        super(publishingYear, startDate, reportingDate, createdBy);
    }

    @Override
    public void validate() throws IllegalArgumentException {
        //TODO: Create validation
    }
}