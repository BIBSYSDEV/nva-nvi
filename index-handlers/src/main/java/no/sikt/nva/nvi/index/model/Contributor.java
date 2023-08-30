package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Contributor(String id,
                          String name,
                          String orcid,
                          List<String> approvals) {

    public static final class Builder {

        private String id;
        private String name;
        private String orcid;
        private List<String> approvals;

        public Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withOrcid(String orcid) {
            this.orcid = orcid;
            return this;
        }

        public Builder withApprovals(List<String> approvals) {
            this.approvals = approvals;
            return this;
        }

        public Contributor build() {
            return new Contributor(id, name, orcid, approvals);
        }
    }
}
