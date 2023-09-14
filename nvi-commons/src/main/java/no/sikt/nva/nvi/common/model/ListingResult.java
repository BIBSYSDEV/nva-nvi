package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.CandidateDao;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record ListingResult(List<CandidateDao> databaseEntries,
                               Map<String, AttributeValue> startMarker,
                               boolean truncated) {
    
    @JsonIgnore
    public boolean isEmpty() {
        return databaseEntries.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }
    public static final class Builder {

        private List<CandidateDao> databaseEntries;
        private Map<String, AttributeValue> startMarker;
        private boolean truncated;

        private Builder() {
        }

        public Builder withDatabaseEntries(List<CandidateDao> databaseEntries) {
            this.databaseEntries = databaseEntries;
            return this;
        }

        public Builder withStartMarker(Map<String, AttributeValue> startMarker) {
            this.startMarker = startMarker;
            return this;
        }

        public Builder withTruncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public ListingResult build() {
            return new ListingResult(databaseEntries, startMarker, truncated);
        }
    }
}
