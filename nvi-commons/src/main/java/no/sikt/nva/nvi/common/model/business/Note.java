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

    public static final String USER_FIELD = "user";
    public static final String TEXT_FIELD = "text";
    public static final String UPDATED_DATE_FIELD = "updatedDate";

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of(USER_FIELD, user.toDynamoDb(),
                   TEXT_FIELD, AttributeValue.fromS(text),
                   UPDATED_DATE_FIELD, AttributeValue.fromN(String.valueOf(createdDate.toEpochMilli()))
            ));
    }

    public static Note fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Note(
            Username.fromDynamoDb(map.get(USER_FIELD)),
            map.get(TEXT_FIELD).s(),
            Instant.ofEpochMilli(Long.parseLong(map.get(UPDATED_DATE_FIELD).n()))
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
