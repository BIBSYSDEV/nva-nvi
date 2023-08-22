package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.PublicationDateDb.Builder;

public class PublicationDateDb implements WithCopy<Builder> {

    public static final String YEAR_FIELD = "year";
    public static final String MONTH_FIELD = "month";
    public static final String DAY = "day";

    @JsonProperty(YEAR_FIELD)
    private String year;
    @JsonProperty(MONTH_FIELD)
    private String month;
    @JsonProperty(DAY)
    private String day;

    public PublicationDateDb(String year, String month, String day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    @Override
    public Builder copy() {
        return new Builder().withYear(year).withMonth(month).withDay(day);
    }

    public String getYear() {
        return year;
    }

    public String getMonth() {
        return month;
    }

    public String getDay() {
        return day;
    }

    public static final class Builder {

        private String year;
        private String month;
        private String day;

        public Builder() {
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public Builder withMonth(String month) {
            this.month = month;
            return this;
        }

        public Builder withDay(String day) {
            this.day = day;
            return this;
        }

        public PublicationDateDb build() {
            return new PublicationDateDb(year, month, day);
        }
    }
}
