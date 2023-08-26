package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Note(Username user,
                   String text,
                   Instant createdDate) {

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of("user", user.toDynamoDb(),
                   "text", AttributeValue.fromS(text),
                   "updatedDate", AttributeValue.fromN(String.valueOf(createdDate.toEpochMilli()))
            ));
    }

    public static Note fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Note(
            Username.fromDynamoDb(map.get("user")),
            map.get("text").s(),
            Instant.ofEpochMilli(Integer.parseInt(map.get("updatedDate").n()))
        );
    }

    public static final class Builder {

        private Username user;
        private String text;
        private Instant createdDate;

        public Builder() {
        }

        public Builder withUser(Username user) {
            this.user = user;
            return this;
        }

        public Builder withText(String text) {
            this.text = text;
            return this;
        }

        public Builder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Note build() {
            return new Note(user, text, createdDate);
        }
    }
}
