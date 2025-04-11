package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.AFFILIATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.CONTRIBUTOR;
import static no.sikt.nva.nvi.test.TestConstants.CREATOR;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ONE;
import static no.sikt.nva.nvi.test.TestConstants.ORCID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ROLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.VERIFICATION_STATUS_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record SampleExpandedContributor(
    URI id,
    String verificationStatus,
    List<String> names,
    String role,
    List<SampleExpandedAffiliation> affiliations,
    String orcId) {

  public static Builder builder() {
    return new Builder();
  }

  public static Builder from(SampleExpandedContributor other) {
    return new Builder(other);
  }

  public ObjectNode asObjectNode() {
    var contributorNode = objectMapper.createObjectNode();
    contributorNode.put(TYPE_FIELD, CONTRIBUTOR);

    contributorNode.set(AFFILIATIONS_FIELD, createAndPopulateAffiliationsNode());

    var roleNode = objectMapper.createObjectNode();
    roleNode.put(TYPE_FIELD, role);
    contributorNode.set(ROLE_FIELD, roleNode);

    var identityNode = objectMapper.createObjectNode();
    identityNode.put(TYPE_FIELD, IDENTITY);
    if (nonNull(id)) {
      identityNode.put(ID_FIELD, id.toString());
    }
    if (nonNull(names) && !names.isEmpty()) {
      if (names.size() > ONE) {
        var nameArrayNode = objectMapper.createArrayNode();
        names.forEach(nameArrayNode::add);
        identityNode.set(NAME_FIELD, nameArrayNode);
      } else {
        identityNode.put(NAME_FIELD, names.getFirst());
      }
    }
    if (nonNull(orcId)) {
      identityNode.put(ORCID_FIELD, orcId);
    }
    if (nonNull(verificationStatus)) {
      identityNode.put(VERIFICATION_STATUS_FIELD, verificationStatus);
    }

    contributorNode.set(IDENTITY_FIELD, identityNode);
    return contributorNode;
  }

  public List<URI> affiliationIds() {
    return affiliations.stream().map(SampleExpandedAffiliation::id).toList();
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
    private List<String> names = List.of(randomString());
    private String role = CREATOR;
    private String verificationStatus;
    private String orcId;
    private List<SampleExpandedAffiliation> affiliations;

    private Builder() {}

    private Builder(SampleExpandedContributor other) {
      this.id = other.id;
      this.verificationStatus = other.verificationStatus;
      this.names = other.names;
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

    public SampleExpandedContributor build() {
      return new SampleExpandedContributor(
          id, verificationStatus, names, role, affiliations, orcId);
    }
  }
}
