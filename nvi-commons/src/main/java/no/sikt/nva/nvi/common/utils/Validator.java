package no.sikt.nva.nvi.common.utils;

import static java.util.Objects.isNull;
import java.time.Instant;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.requests.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.requests.UpsertPeriodRequest;

public final class Validator {

    private Validator() {
    }

    public static void hasValidLength(Integer year, int length) {
        if (year.toString().length() != length) {
            throw new IllegalArgumentException("Provided period has invalid length! Expected length: " + length);
        }
    }

    public static void isBefore(Instant startDate, Instant endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date can not be after end date!");
        }
    }

    public static void isNotBeforeCurrentTime(Instant date) {
        if (date.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Provided date is back in time!");
        }
    }

    public static void doesNotHaveNullValues(UpsertPeriodRequest upsertPeriodRequest) {
        if (isNull(upsertPeriodRequest.publishingYear())) {
            throw new IllegalArgumentException("Publishing year can not be null!");
        }
        if (isNull(upsertPeriodRequest.startDate())) {
            throw new IllegalArgumentException("Start date can not be null!");
        }
        if (isNull(upsertPeriodRequest.reportingDate())) {
            throw new IllegalArgumentException("Reporting date can not be null!");
        }
        if (upsertPeriodRequest instanceof UpdatePeriodRequest updateRequest) {
            if (isNull(updateRequest.modifiedBy())) {
                throw new IllegalArgumentException("Modified by can not be null!");
            }
        }
        if (upsertPeriodRequest instanceof CreatePeriodRequest createRequest) {
            if (isNull(createRequest.createdBy())) {
                throw new IllegalArgumentException("Created by can not be null!");
            }
        }
    }
}
