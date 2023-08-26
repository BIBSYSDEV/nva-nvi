package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.Map;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Period(int year, Instant start, Instant end) {

    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of("year", AttributeValue.fromN(String.valueOf(year)),
                   "start", AttributeValue.fromN(String.valueOf(start.toEpochMilli())),
                   "end", AttributeValue.fromN(String.valueOf(end.toEpochMilli()))
            ));
    }
    @JacocoGenerated //TODO: Will be used in Period, in next DB task
    public static Period fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Period(
            Integer.parseInt(map.get("year").n()),
            Instant.ofEpochMilli(Integer.parseInt(map.get("start").n())),
            Instant.ofEpochMilli(Integer.parseInt(map.get("end").n()))
        );
    }

    public static final class Builder {

        private int year;
        private Instant start;
        private Instant end;

        public Builder() {
        }

        public Builder withYear(int year) {
            this.year = year;
            return this;
        }

        public Builder withStart(Instant start) {
            this.start = start;
            return this;
        }

        public Builder withEnd(Instant end) {
            this.end = end;
            return this;
        }

        public Period build() {
            return new Period(year, start, end);
        }
    }
}
