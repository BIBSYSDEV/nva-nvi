package no.sikt.nva.nvi.index.model.search;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import no.unit.nva.commons.json.JsonSerializable;

public record CandidateSearchParameters(String searchTerm,
                                        List<URI> affiliations,
                                        boolean excludeSubUnits,
                                        String filter,
                                        String username,
                                        String year,
                                        String category,
                                        String title,
                                        String contributor,
                                        String assignee,
                                        URI topLevelCristinOrg,
                                        SearchResultParameters searchResultParameters) implements JsonSerializable {

    public static Builder builder() {
        return new Builder();
    }

    public String topLevelOrgUriAsString() {
        return Optional.ofNullable(topLevelCristinOrg).map(URI::toString).orElse(null);
    }

    public static final class Builder {

        private String searchTerm;
        private List<URI> affiliations;
        private boolean excludeSubUnits;
        private String filter;
        private String username;
        private String year;
        private String category;
        private String title;
        private String contributor;
        private String assignee;
        private URI topLevelCristinOrg;
        private SearchResultParameters searchResultParameters = SearchResultParameters.builder().build();

        private Builder() {
        }

        public Builder withSearchTerm(String searchTerm) {
            this.searchTerm = searchTerm;
            return this;
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

        public Builder withTopLevelCristinOrg(URI topLevelCristinOrg) {
            this.topLevelCristinOrg = topLevelCristinOrg;
            return this;
        }

        public Builder withSearchResultParameters(SearchResultParameters searchResultParameters) {
            this.searchResultParameters = searchResultParameters;
            return this;
        }

        public CandidateSearchParameters build() {
            return new CandidateSearchParameters(searchTerm, affiliations, excludeSubUnits, filter, username, year,
                                                 category, title, contributor, assignee, topLevelCristinOrg,
                                                 searchResultParameters);
        }
    }
}
