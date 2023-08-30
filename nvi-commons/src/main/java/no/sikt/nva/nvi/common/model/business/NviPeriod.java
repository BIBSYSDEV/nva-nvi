package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviPeriod(String publishingYear,
                        Instant reportingDate,
                        Username createdBy,
                        Username modifiedBy) {

    public static final String PUBLISHING_YEAR_FIELD = "publishingYear";
    public static final String REPORTING_DATA_FIELD = "reportingDate";
    public static final String CREATED_BY_FIELD = "createdBy";
    public static final String MODIFIED_BY_FIELD = "modifiedBy";

    public AttributeValue toDynamoDb() {
        var map = new HashMap<String, AttributeValue>();
        map.put(PUBLISHING_YEAR_FIELD, AttributeValue.fromS(publishingYear));
        if (reportingDate != null) {
            map.put(REPORTING_DATA_FIELD, AttributeValue.fromN(String.valueOf(reportingDate.toEpochMilli())));
        }
        map.put(CREATED_BY_FIELD, createdBy.toDynamoDb());
        if (modifiedBy != null) {
            map.put(MODIFIED_BY_FIELD, modifiedBy.toDynamoDb());
        }
        return AttributeValue.fromM(map);
    }

    public static NviPeriod fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();

        return new Builder()
                   .withPublishingYear(map.get(PUBLISHING_YEAR_FIELD).s())
                   .withReportingDate(
                       Optional.ofNullable(map.get(REPORTING_DATA_FIELD))
                           .map(AttributeValue::n).map(Long::parseLong).map(Instant::ofEpochMilli)
                           .orElse(null)
                   )
                   .withCreatedBy(Username.fromDynamoDb(map.get(CREATED_BY_FIELD)))
                   .withModifiedBy(
                       Optional.ofNullable(map.get(MODIFIED_BY_FIELD)).map(Username::fromDynamoDb).orElse(null)
                   )
                   .build();
    }

    public Builder copy() {
        return new Builder()
                   .withPublishingYear(publishingYear)
                   .withReportingDate(reportingDate)
                   .withCreatedBy(createdBy)
                   .withModifiedBy(modifiedBy);
    }

    public static final class Builder {

        private String publishingYear;
        private Instant reportingDate;
        private Username createdBy;
        private Username modifiedBy;

        public Builder() {
        }

        public Builder withPublishingYear(String publishingYear) {
            this.publishingYear = publishingYear;
            return this;
        }

        public Builder withReportingDate(Instant reportingDate) {
            this.reportingDate = reportingDate;
            return this;
        }

        public Builder withCreatedBy(Username createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder withModifiedBy(Username modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }

        public NviPeriod build() {
            return new NviPeriod(publishingYear, reportingDate, createdBy, modifiedBy);
        }
    }
}
