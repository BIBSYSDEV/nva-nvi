package no.sikt.nva.nvi.common.service.dto;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import no.sikt.nva.nvi.common.model.PeriodStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeName(NviPeriodDto.NVI_PERIOD)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record NviPeriodDto(
    URI id, String publishingYear, String startDate, String reportingDate, PeriodStatus status) {

  public static final String NVI_PERIOD = "NviPeriod";

  public static NviPeriodDto from(NviPeriod period) {
    return isNull(period) ? noPeriod() : period.toDto();
  }

  private static NviPeriodDto noPeriod() {
    return new NviPeriodDto(null, null, null, null, PeriodStatus.NONE);
  }
}
