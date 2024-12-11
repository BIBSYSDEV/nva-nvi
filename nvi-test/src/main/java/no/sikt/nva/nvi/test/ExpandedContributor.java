package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.CONTRIBUTOR;
import static no.sikt.nva.nvi.test.TestConstants.CREATOR;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ROLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record ExpandedContributor(URI id, String verificationStatus, String contributorName, String role,
                                  List<ExpandedAffiliation> affiliations, String orcId) {



    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(ExpandedContributor other) {
        return new Builder(other);
    }

    public ObjectNode asObjectNode() {
        var contributorNode = objectMapper.createObjectNode();
        contributorNode.put(TYPE_FIELD, CONTRIBUTOR);

        contributorNode.set("affiliations", createAndPopulateAffiliationsNode());

        var roleNode = objectMapper.createObjectNode();
        roleNode.put(TYPE_FIELD, role);
        contributorNode.set(ROLE_FIELD, roleNode);

        var identityNode = objectMapper.createObjectNode();
        identityNode.put(TYPE_FIELD, IDENTITY);
        if (nonNull(id)) {
            identityNode.put(ID_FIELD, id.toString());
        }
        if (nonNull(contributorName)) {
            identityNode.put(NAME_FIELD, contributorName);
        }
        if (nonNull(orcId)) {
            identityNode.put("orcid", orcId);
        }
        if (nonNull(verificationStatus)) {
            identityNode.put("verificationStatus", verificationStatus);
        }

        contributorNode.set(IDENTITY_FIELD, identityNode);
        return contributorNode;
    }

    public List<URI> affiliationIds() {
        return affiliations.stream()
                           .map(ExpandedAffiliation::id)
                           .toList();
    }

    private ArrayNode createAndPopulateAffiliationsNode() {
        var affiliationsRootNode = objectMapper.createArrayNode();

        if (nonNull(affiliations)) {
            affiliations.forEach(affiliation -> affiliationsRootNode.add(affiliation.asObjectNode()));
        }
        return affiliationsRootNode;
    }

    @JacocoGenerated
    public static final class Builder {

        private URI id = randomUri();
        private String contributorName = randomString();
        private String role = CREATOR;
        private String verificationStatus;
        private String orcId;
        private List<ExpandedAffiliation> affiliations;

        private Builder() {
        }

        private Builder(ExpandedContributor other) {
            this.id = other.id;
            this.verificationStatus = other.verificationStatus;
            this.contributorName = other.contributorName;
            this.role = other.role;
            this.affiliations = other.affiliations;
            this.orcId = other.orcId;
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withVerificationStatus(String verificationStatus) {
            this.verificationStatus = verificationStatus;
            return this;
        }

        public Builder withName(String contributorName) {
            this.contributorName = contributorName;
            return this;
        }

        public Builder withAffiliations(List<ExpandedAffiliation> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public ExpandedContributor build() {
            return new ExpandedContributor(id, verificationStatus, contributorName, role, affiliations, orcId);
        }
    }
}
