package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.AFFILIATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.CONTRIBUTOR;
import static no.sikt.nva.nvi.test.TestConstants.CREATOR;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ORCID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ROLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.VERIFICATION_STATUS_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.createNodeWithType;
import static no.sikt.nva.nvi.test.TestUtils.putAsArrayIfMultipleValues;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotBlank;
import static no.sikt.nva.nvi.test.TestUtils.putIfNotNull;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collection;
import java.util.List;

public record SampleExpandedContributor(
    URI id,
    Collection<String> verificationStatus,
    List<String> names,
    String role,
    List<SampleExpandedAffiliation> affiliations,
    String orcId) {

  public static Builder builder() {
    return new Builder();
  }

  public ObjectNode asObjectNode() {
    var contributorNode = createNodeWithType(CONTRIBUTOR);

    contributorNode.set(AFFILIATIONS_FIELD, createAndPopulateAffiliationsNode());

    var roleNode = objectMapper.createObjectNode();
    roleNode.put(TYPE_FIELD, role);
    contributorNode.set(ROLE_FIELD, roleNode);

    var identityNode = createNodeWithType(IDENTITY);
    identityNode.put(TYPE_FIELD, IDENTITY);
    putIfNotNull(identityNode, ID_FIELD, id);
    putAsArrayIfMultipleValues(identityNode, NAME_FIELD, names);
    putIfNotBlank(identityNode, ORCID_FIELD, orcId);
    putAsArrayIfMultipleValues(identityNode, VERIFICATION_STATUS_FIELD, verificationStatus);

    contributorNode.set(IDENTITY_FIELD, identityNode);
    return contributorNode;
  }

  private ArrayNode createAndPopulateAffiliationsNode() {
    var affiliationsRootNode = objectMapper.createArrayNode();

    if (nonNull(affiliations)) {
      affiliations.forEach(affiliation -> affiliationsRootNode.add(affiliation.asObjectNode()));
    }
    return affiliationsRootNode;
  }

  public static final class Builder {

    private URI id = randomUri();
    private List<String> names = List.of(randomString());
    private String role = CREATOR;
    private Collection<String> verificationStatus = List.of("NotVerified");
    private String orcId;
    private List<SampleExpandedAffiliation> affiliations;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withVerificationStatus(String verificationStatus) {
      this.verificationStatus = List.of(verificationStatus);
      return this;
    }

    public Builder withVerificationStatus(Collection<String> verificationStatus) {
      this.verificationStatus = List.copyOf(verificationStatus);
      return this;
    }

    public Builder withNames(List<String> names) {
      this.names = names;
      return this;
    }

    public Builder withRole(String role) {
      this.role = role;
      return this;
    }

    public Builder withAffiliations(List<SampleExpandedAffiliation> affiliations) {
      this.affiliations = affiliations;
      return this;
    }

    public Builder withOrcId(String orcId) {
      this.orcId = orcId;
      return this;
    }

    public SampleExpandedContributor build() {
      return new SampleExpandedContributor(
          id, verificationStatus, names, role, affiliations, orcId);
    }
  }
}
