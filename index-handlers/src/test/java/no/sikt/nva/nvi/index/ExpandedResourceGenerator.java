package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.AFFILIATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.CONTRIBUTORS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.EN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.IDENTITY_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LABELS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LEVEL_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.MAIN_TITLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NAME_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NB_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ORCID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PAGES_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_CONTEXT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_DATE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_INSTANCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ROLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TOP_LEVEL_ORGANIZATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDate;
import no.sikt.nva.nvi.common.utils.JsonUtils;

public final class ExpandedResourceGenerator {

  private final boolean populateLanguage;
  private final boolean populateIssn;
  private final boolean populateAbstract;
  private final Candidate candidate;

  private ExpandedResourceGenerator(
      boolean populateLanguage,
      boolean populateIssn,
      boolean populateAbstract,
      Candidate candidate) {
    this.populateLanguage = populateLanguage;
    this.populateIssn = populateIssn;
    this.populateAbstract = populateAbstract;
    this.candidate = candidate;
  }

  public static String extractOptionalAbstract(JsonNode expandedResource) {
    return JsonUtils.extractOptJsonNodeTextValue(expandedResource, "/entityDescription/abstract")
        .orElse(null);
  }

  public static String extractTitle(JsonNode expandedResource) {
    return JsonUtils.extractJsonNodeTextValue(expandedResource, "/entityDescription/mainTitle");
  }

  public static ArrayNode extractContributors(JsonNode expandedResource) {
    return (ArrayNode) expandedResource.at("/entityDescription/contributors");
  }

  public static List<URI> extractAffiliations(JsonNode contributorNode) {
    return JsonUtils.streamNode(contributorNode.at("/affiliations"))
        .map(affiliationNode -> affiliationNode.at("/id"))
        .map(JsonNode::asText)
        .map(URI::create)
        .toList();
  }

  public static String extractId(JsonNode contributorNode) {
    return JsonUtils.extractJsonNodeTextValue(contributorNode, "/identity/id");
  }

  public static String extractName(JsonNode contributorNode) {
    return JsonUtils.extractJsonNodeTextValue(contributorNode, "/identity/name");
  }

  public static String extractOrcid(JsonNode contributorNode) {
    return JsonUtils.extractJsonNodeTextValue(contributorNode, "/identity/orcid");
  }

  public static String extractRole(JsonNode contributorNode) {
    return JsonUtils.extractJsonNodeTextValue(contributorNode, "/role/type");
  }

  public static String extractType(JsonNode expandedResource) {
    return JsonUtils.extractJsonNodeTextValue(
        expandedResource, "/entityDescription/reference/publicationInstance/type");
  }

  public static Builder builder() {
    return new Builder();
  }

  public JsonNode createExpandedResource() {
    return createExpandedResource(Collections.emptyList());
  }

  public JsonNode createExpandedResource(List<URI> nonNviContributorAffiliationIds) {
    var root = objectMapper.createObjectNode();

    root.put(ID_FIELD, candidate.getPublicationId().toString());

    var entityDescription = objectMapper.createObjectNode();

    var contributors = populateAndCreateContributors(candidate, nonNviContributorAffiliationIds);

    entityDescription.set(CONTRIBUTORS_FIELD, contributors);

    entityDescription.put(MAIN_TITLE_FIELD, randomString());

    var publicationDate =
        createAndPopulatePublicationDate(candidate.getPublicationDetails().publicationDate());

    entityDescription.set(PUBLICATION_DATE_FIELD, publicationDate);

    var reference = objectMapper.createObjectNode();

    var publicationInstance = createAndPopulatePublicationInstance(candidate);
    reference.set(PUBLICATION_INSTANCE_FIELD, publicationInstance);

    var publicationContext = createAndPopulatePublicationContext(candidate, populateIssn);
    reference.set(PUBLICATION_CONTEXT_FIELD, publicationContext);

    entityDescription.set(REFERENCE_FIELD, reference);
    if (populateLanguage) {
      entityDescription.put("language", "http://lexvo.org/id/iso639-3/nob");
    }

    if (populateAbstract) {
      entityDescription.put("abstract", randomString());
    }

    root.set(ENTITY_DESCRIPTION_FIELD, entityDescription);

    root.put(IDENTIFIER_FIELD, candidate.getIdentifier().toString());

    var topLevelOrganizations = createAndPopulateTopLevelOrganizations(candidate);

    root.set(TOP_LEVEL_ORGANIZATIONS_FIELD, topLevelOrganizations);

    return root;
  }

  private static ObjectNode createAndPopulatePublicationInstance(Candidate candidate) {
    var publicationInstance = objectMapper.createObjectNode();
    var publicationType = candidate.getPublicationDetails().publicationType().getValue();
    publicationInstance.put(TYPE_FIELD, publicationType);
    switch (publicationType) {
      case "AcademicArticle", "AcademicLiteratureReview", "AcademicChapter" -> {
        var pages = objectMapper.createObjectNode();
        pages.put("begin", "pageBegin");
        pages.put("end", "pageEnd");
        publicationInstance.set(PAGES_FIELD, pages);
      }
      case "AcademicMonograph" -> {
        var pages = objectMapper.createObjectNode();
        pages.put(PAGES_FIELD, "numberOfPages");
        publicationInstance.set(PAGES_FIELD, pages);
      }
      default -> {
        // do nothing
      }
    }
    return publicationInstance;
  }

  private static ObjectNode createAndPopulatePublicationContext(
      Candidate candidate, boolean populateIssn) {
    if (isNull(candidate.getPublicationChannelType())) {
      return objectMapper.createObjectNode();
    }
    return switch (candidate.getPublicationChannelType()) {
      case JOURNAL -> createJournalPublicationContext(candidate, populateIssn);
      case SERIES -> createSeriesPublicationContext(candidate, populateIssn);
      case PUBLISHER -> createPublisherPublicationContext(candidate);
    };
  }

  private static ObjectNode createPublisherPublicationContext(Candidate candidate) {
    var publisher = objectMapper.createObjectNode();
    publisher.put(TYPE_FIELD, "Publisher");
    publisher.put(ID_FIELD, candidate.getPublicationChannelId().toString());
    publisher.put(LEVEL_FIELD, candidate.getScientificLevel().getValue());
    publisher.put(NAME_FIELD, randomString());
    var publicationContext = objectMapper.createObjectNode();
    publicationContext.set("publisher", publisher);
    return publicationContext;
  }

  private static ObjectNode createSeriesPublicationContext(
      Candidate candidate, boolean populateIssn) {
    var series = objectMapper.createObjectNode();
    series.put(TYPE_FIELD, "Series");
    series.put(ID_FIELD, candidate.getPublicationChannelId().toString());
    series.put(LEVEL_FIELD, candidate.getScientificLevel().getValue());
    series.put(NAME_FIELD, randomString());
    if (populateIssn) {
      series.put("printIssn", randomString());
    }
    var publicationContext = objectMapper.createObjectNode();
    publicationContext.set("series", series);
    return publicationContext;
  }

  private static ObjectNode createJournalPublicationContext(
      Candidate candidate, boolean populateIssn) {
    var journal = objectMapper.createObjectNode();
    journal.put(TYPE_FIELD, "Journal");
    journal.put(ID_FIELD, candidate.getPublicationChannelId().toString());
    journal.put(LEVEL_FIELD, candidate.getScientificLevel().getValue());
    journal.put(NAME_FIELD, randomString());
    if (populateIssn) {
      journal.put("printIssn", randomString());
    }
    return journal;
  }

  private static JsonNode createAndPopulateTopLevelOrganizations(Candidate candidate) {
    var topLevelOrganizations = objectMapper.createArrayNode();
    candidate.getPublicationDetails().getNviCreators().stream()
        .map(NviCreatorDto::affiliations)
        .flatMap(List::stream)
        .distinct()
        .map(URI::toString)
        .map(ExpandedResourceGenerator::createOrganizationNode)
        .forEach(topLevelOrganizations::add);
    return topLevelOrganizations;
  }

  private static JsonNode createOrganizationNode(String affiliationId) {
    var organization = objectMapper.createObjectNode();
    organization.put(ID_FIELD, affiliationId);
    organization.put(TYPE_FIELD, "Organization");
    var labels = objectMapper.createObjectNode();
    labels.put(NB_FIELD, HARDCODED_NORWEGIAN_LABEL);
    labels.put(EN_FIELD, HARDCODED_ENGLISH_LABEL);
    organization.set(LABELS_FIELD, labels);
    return organization;
  }

  private static ObjectNode createAndPopulatePublicationDate(PublicationDate date) {
    var publicationDate = objectMapper.createObjectNode();
    publicationDate.put(TYPE_FIELD, "PublicationDate");
    if (nonNull(date.day())) {
      publicationDate.put("day", date.day());
    }
    if (nonNull(date.month())) {
      publicationDate.put("month", date.month());
    }
    publicationDate.put("year", date.year());
    return publicationDate;
  }

  private static ArrayNode populateAndCreateContributors(
      Candidate candidate, List<URI> nonNviContributorAffiliationIds) {

    var contributors = objectMapper.createArrayNode();

    var verifiedCreators = candidate.getPublicationDetails().verifiedCreators();
    verifiedCreators.stream()
        .map(creator -> createContributorNode(creator.affiliations(), creator.id(), randomString()))
        .forEach(contributors::add);

    var unverifiedCreators = candidate.getPublicationDetails().unverifiedCreators();
    unverifiedCreators.stream()
        .map(
            unverifiedCreator ->
                createContributorNode(
                    unverifiedCreator.affiliations(), null, unverifiedCreator.name()))
        .forEach(contributors::add);

    addOtherRandomContributors(contributors, nonNviContributorAffiliationIds);
    return contributors;
  }

  private static void addOtherRandomContributors(
      ArrayNode contributors, List<URI> affiliationsIds) {
    IntStream.range(0, 10)
        .mapToObj(
            i -> createContributorNode(affiliationsIds, URI.create(randomString()), randomString()))
        .forEach(contributors::add);
  }

  private static ObjectNode createContributorNode(
      List<URI> affiliationsUris, URI contributorId, String contributorName) {
    var contributorNode = objectMapper.createObjectNode();

    contributorNode.put(TYPE_FIELD, "Contributor");

    var affiliations = createAndPopulateAffiliationsNode(affiliationsUris);

    contributorNode.set(AFFILIATIONS_FIELD, affiliations);

    var role = objectMapper.createObjectNode();
    role.put(TYPE_FIELD, randomString());
    contributorNode.set(ROLE_FIELD, role);

    var identity = objectMapper.createObjectNode();

    if (nonNull(contributorId)) {
      identity.put(ID_FIELD, contributorId.toString());
    }

    if (nonNull(contributorName)) {
      identity.put(NAME_FIELD, contributorName);
    }

    identity.put(ORCID_FIELD, randomString());

    contributorNode.set(IDENTITY_FIELD, identity);
    return contributorNode;
  }

  private static ArrayNode createAndPopulateAffiliationsNode(List<URI> creatorAffiliations) {
    var affiliations = objectMapper.createArrayNode();

    creatorAffiliations.forEach(
        affiliation -> {
          var affiliationNode = objectMapper.createObjectNode();
          affiliationNode.put(ID_FIELD, affiliation.toString());
          affiliationNode.put(TYPE_FIELD, "Organization");
          var labels = objectMapper.createObjectNode();

          labels.put(NB_FIELD, HARDCODED_NORWEGIAN_LABEL);
          labels.put(EN_FIELD, HARDCODED_ENGLISH_LABEL);

          affiliationNode.set(LABELS_FIELD, labels);

          affiliations.add(affiliationNode);
        });
    return affiliations;
  }

  public static final class Builder {

    private boolean populateLanguage;
    private boolean populateIssn;
    private boolean populateAbstract;

    private Candidate candidate;

    private Builder() {}

    public Builder withPopulateLanguage(boolean populateLanguage) {
      this.populateLanguage = populateLanguage;
      return this;
    }

    public Builder withPopulateIssn(boolean populateIssn) {
      this.populateIssn = populateIssn;
      return this;
    }

    public Builder withPopulateAbstract(boolean populateAbstract) {
      this.populateAbstract = populateAbstract;
      return this;
    }

    public Builder withCandidate(Candidate candidate) {
      this.candidate = candidate;
      return this;
    }

    public ExpandedResourceGenerator build() {
      return new ExpandedResourceGenerator(
          populateLanguage, populateIssn, populateAbstract, candidate);
    }
  }
}
