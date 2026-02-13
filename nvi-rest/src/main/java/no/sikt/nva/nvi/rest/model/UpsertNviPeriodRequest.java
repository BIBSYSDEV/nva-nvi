package no.sikt.nva.nvi.rest.model;

import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeName(UpsertNviPeriodRequest.NVI_PERIOD)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UpsertNviPeriodRequest(
    String publishingYear, String startDate, String reportingDate) {

  public static final String INVALID_DATE_FORMAT = "Invalid date format!";
  public static final String NVI_PERIOD = "NviPeriod";

  public CreatePeriodRequest.Builder toCreatePeriodRequest() {
    return CreatePeriodRequest.builder()
        .withPublishingYear(Integer.parseInt(publishingYear))
        .withStartDate(toInstant(startDate))
        .withReportingDate(toInstant(reportingDate));
  }

  public UpdatePeriodRequest.Builder toUpdatePeriodRequest() {
    return UpdatePeriodRequest.builder()
        .withPublishingYear(Integer.parseInt(publishingYear))
        .withStartDate(toInstant(startDate))
        .withReportingDate(toInstant(reportingDate));
  }

  private Instant toInstant(String reportingDate) {
    return attempt(() -> Instant.parse(reportingDate))
        .orElseThrow(failure -> new IllegalArgumentException(INVALID_DATE_FORMAT));
  }
}
