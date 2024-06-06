package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeName(NviPeriodDto.NVI_PERIOD)
@JsonTypeInfo(use = Id.NAME, property = "type")
public record NviPeriodDto(URI id,
                           String publishingYear,
                           String startDate,
                           String reportingDate) {

    public static final String NVI_PERIOD = "NviPeriod";
}
