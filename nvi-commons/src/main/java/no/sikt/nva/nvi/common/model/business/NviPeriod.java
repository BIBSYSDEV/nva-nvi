package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.Map;
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

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of(PUBLISHING_YEAR_FIELD, AttributeValue.fromN(publishingYear),
                   REPORTING_DATA_FIELD, AttributeValue.fromN(String.valueOf(reportingDate.toEpochMilli())),
                   CREATED_BY_FIELD, createdBy.toDynamoDb(),
                   MODIFIED_BY_FIELD, modifiedBy.toDynamoDb()
            ));
    }

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public static NviPeriod fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new NviPeriod(
            map.get(PUBLISHING_YEAR_FIELD).s(),
            Instant.ofEpochMilli(Integer.parseInt(map.get(REPORTING_DATA_FIELD).n())),
            Username.fromDynamoDb(map.get(CREATED_BY_FIELD)),
            Username.fromDynamoDb(map.get(MODIFIED_BY_FIELD))
        );
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
