package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.UsernameDb.Builder;
import no.sikt.nva.nvi.common.model.business.Username;
import nva.commons.core.JacocoGenerated;

public class UsernameDb implements WithCopy<Builder> {

    public static final String VALUE_FIELD = "value";
    @JsonProperty(VALUE_FIELD)
    private String value;

    @JacocoGenerated
    public UsernameDb() {
    }

    public UsernameDb(String value) {
        this.value = value;
    }

    @Override
    public Builder copy() {
        return new Builder().withValue(value);
    }

    public String getValue() {
        return value;
    }

    public static final class Builder {

        private String value;

        public Builder() {
        }

        public Builder withValue(String value) {
            this.value = value;
            return this;
        }

        public UsernameDb build() {
            return new UsernameDb(value);
        }
    }
}
