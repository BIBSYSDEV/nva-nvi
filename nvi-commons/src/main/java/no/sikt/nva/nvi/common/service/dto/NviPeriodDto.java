package no.sikt.nva.nvi.common.service.dto;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;

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
