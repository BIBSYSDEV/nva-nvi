package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(URI institutionId,
                             Status status,
                             Username finalizedBy,
                             Instant finalizedDate) {

    public AttributeValue toDynamoDb() {
        var map = new HashMap<String, AttributeValue>();
        map.put("institutionId", AttributeValue.fromS(institutionId.toString()));
        map.put("status", AttributeValue.fromS(status.getValue()));
        if (finalizedBy != null) map.put("finalizedBy", finalizedBy.toDynamoDb());
        if (finalizedDate != null) map.put("finalizedDate", AttributeValue.fromN(String.valueOf(finalizedDate.toEpochMilli())));
        return AttributeValue.fromM(map);
    }

    public static ApprovalStatus fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new ApprovalStatus(
            URI.create(map.get("institutionId").s()),
            Status.parse(map.get("status").s()),
            Optional.ofNullable(map.get("finalizedBy")).map(Username::fromDynamoDb).orElse(null),
            Optional.ofNullable(map.get("finalizedDate")).map(AttributeValue::n).map(Long::parseLong).map(Instant::ofEpochMilli).orElse(null)
        );
    }

    public static final class Builder {

        private URI institutionId;
        private Status status;
        private Username finalizedBy;
        private Instant finalizedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withFinalizedBy(Username finalizedBy) {
            this.finalizedBy = finalizedBy;
            return this;
        }

        public Builder withFinalizedDate(Instant finalizedDate) {
            this.finalizedDate = finalizedDate;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(institutionId, status, finalizedBy, finalizedDate);
        }
    }
}
