package no.sikt.nva.nvi.common.service.model;

import java.time.Instant;

public class UpdatePeriodRequest extends UpsertPeriodRequest {

    public UpdatePeriodRequest(Integer publishingYear, Instant startDate, Instant reportingDate, Username createdBy) {
        super(publishingYear, startDate, reportingDate, createdBy);
    }

    @Override
    public void validate() throws IllegalArgumentException {
        //TODO: Update validation
    }
}
