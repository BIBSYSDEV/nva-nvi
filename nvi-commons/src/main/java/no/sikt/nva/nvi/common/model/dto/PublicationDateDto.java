package no.sikt.nva.nvi.common.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record PublicationDateDto(@JsonProperty(YEAR_FIELD) String year, @JsonProperty(MONTH_FIELD) String month,
                                 @JsonProperty(DAY_FIELD) String day) {

    public static final String YEAR_FIELD = "year";
    public static final String MONTH_FIELD = "month";
    public static final String DAY_FIELD = "day";
}
