package no.sikt.nva.nvi.index.model;

import static nva.commons.core.StringUtils.EMPTY_STRING;
import java.net.URI;
import nva.commons.core.JacocoGenerated;

public record CandidateSearchParameters(String affiliations, boolean excludeSubUnits, String filter, String username,
                                        String year, URI customer, int offset, int size) {
    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated
    public static final class Builder {

        private String affiliations = null;
        private boolean excludeSubUnits = false;
        private String filter = EMPTY_STRING;
        private String username;
        private String year;
        private URI customer;
        private int offset = 0;
        private int size = 10;

        public Builder() {
        }

        public Builder withAffiliations(String affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public Builder withExcludeSubUnits(boolean excludeSubUnits) {
            this.excludeSubUnits = excludeSubUnits;
            return this;
        }

        public Builder withFilter(String filter) {
            this.filter = filter;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public Builder withCustomer(URI customer) {
            this.customer = customer;
            return this;
        }

        public Builder withOffset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder withSize(int size) {
            this.size = size;
            return this;
        }

        public CandidateSearchParameters build() {
            return new CandidateSearchParameters(affiliations,
                                                 excludeSubUnits,
                                                 filter,
                                                 username,
                                                 year,
                                                 customer,
                                                 offset,
                                                 size);
        }
    }
}
