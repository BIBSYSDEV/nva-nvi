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

    public static final String INSTITUTION_ID_FIELD = "institutionId";
    public static final String STATUS_FIELD = "status";
    public static final String FINALIZED_BY_FIELD = "finalizedBy";
    public static final String FINALIZED_DATE_FIELD = "finalizedDate";

    public AttributeValue toDynamoDb() {
        var map = new HashMap<String, AttributeValue>();
        // Create fields for all strings below
        map.put(INSTITUTION_ID_FIELD, AttributeValue.fromS(institutionId.toString()));
        map.put(STATUS_FIELD, AttributeValue.fromS(status.getValue()));
        if (finalizedBy != null) {
            map.put(FINALIZED_BY_FIELD, finalizedBy.toDynamoDb());
        }
        if (finalizedDate != null) {
            map.put(FINALIZED_DATE_FIELD, AttributeValue.fromN(String.valueOf(finalizedDate.toEpochMilli())));
        }
        return AttributeValue.fromM(map);
    }

    public static ApprovalStatus fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Builder()
                   .withInstitutionId(URI.create(map.get(INSTITUTION_ID_FIELD).s()))
                   .withStatus(Status.parse(map.get(STATUS_FIELD).s()))
                   .withFinalizedBy(
                       Optional.ofNullable(map.get(FINALIZED_BY_FIELD)).map(Username::fromDynamoDb).orElse(null))
                   .withFinalizedDate(Optional.ofNullable(map.get(FINALIZED_DATE_FIELD))
                                          .map(AttributeValue::n)
                                          .map(Long::parseLong)
                                          .map(Instant::ofEpochMilli)
                                          .orElse(null))
                   .build();
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
