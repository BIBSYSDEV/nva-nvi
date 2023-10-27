package no.sikt.nva.nvi.index.model;

import static nva.commons.core.StringUtils.EMPTY_STRING;
import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

public record CandidateSearchParameters(List<URI> affiliations, boolean excludeSubUnits, String filter, String username,
                                        String year, String category, String title, String contributor,
                                        String assignee, URI customer,
                                        int offset,
                                        int size) {
    public static Builder builder() {
        return new Builder();
    }

    @JacocoGenerated
    public static final class Builder {

        private List<URI> affiliations;
        private boolean excludeSubUnits;
        private String filter = EMPTY_STRING;
        private String username;
        private String year;
        private String category;
        private String title;
        private String contributor;
        private String assignee;
        private URI customer;
        private int offset;
        private int size = 10;

        public Builder() {
        }

        public Builder withAffiliations(List<URI> affiliations) {
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

        public Builder withCategory(String category) {
            this.category = category;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withContributor(String contributor) {
            this.contributor = contributor;
            return this;
        }

        public Builder withAssignee(String assignee) {
            this.assignee = assignee;
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
                                                 category,
                                                 title,
                                                 contributor,
                                                 assignee,
                                                 customer,
                                                 offset,
                                                 size);
        }
    }
}
