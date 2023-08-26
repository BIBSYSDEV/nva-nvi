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

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of("institutionId", AttributeValue.fromS(institutionId.toString()),
                   "period", period.toDynamoDb(),
                   "status", AttributeValue.fromS(status.getValue()),
                   "updatedDate", AttributeValue.fromN(String.valueOf(updatedDate.toEpochMilli()))
            ));
    }

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public ReportingStatus fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new ReportingStatus(
            URI.create(map.get("institutionId").s()),
            Period.fromDynamoDb(map.get("period")),
            CompletionStatus.valueOf(map.get("status").s()),
            Instant.ofEpochMilli(Integer.parseInt(map.get("updatedDate").n()))
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
