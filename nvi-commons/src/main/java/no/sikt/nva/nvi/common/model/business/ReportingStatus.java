package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record ReportingStatus(URI institutionId,
                              Period period,
                              CompletionStatus status,
                              Instant updatedDate) {

    public static final String INSTITUTION_ID_FIELD = "institutionId";
    public static final String PERIOD_FIELD = "period";
    public static final String STATUS_FIELD = "status";
    public static final String UPDATED_DATE_FIELD = "updatedDate";

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of(INSTITUTION_ID_FIELD, AttributeValue.fromS(institutionId.toString()),
                   PERIOD_FIELD, period.toDynamoDb(),
                   STATUS_FIELD, AttributeValue.fromS(status.getValue()),
                   UPDATED_DATE_FIELD, AttributeValue.fromN(String.valueOf(updatedDate.toEpochMilli()))
            ));
    }

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public ReportingStatus fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new ReportingStatus(
            URI.create(map.get(INSTITUTION_ID_FIELD).s()),
            Period.fromDynamoDb(map.get(PERIOD_FIELD)),
            CompletionStatus.valueOf(map.get(STATUS_FIELD).s()),
            Instant.ofEpochMilli(Integer.parseInt(map.get(UPDATED_DATE_FIELD).n()))
        );
    }

    public static final class Builder {

        private URI institutionId;
        private Period period;
        private CompletionStatus status;
        private Instant updatedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withPeriod(Period period) {
            this.period = period;
            return this;
        }

        public Builder withStatus(CompletionStatus status) {
            this.status = status;
            return this;
        }

        public Builder withUpdatedDate(Instant updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public ReportingStatus build() {
            return new ReportingStatus(institutionId, period, status, updatedDate);
        }
    }
}
