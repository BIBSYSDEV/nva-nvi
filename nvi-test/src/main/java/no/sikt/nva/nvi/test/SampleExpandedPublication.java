package no.sikt.nva.nvi.test;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.ABSTRACT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.BODY_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.CONTRIBUTORS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ENTITY_DESCRIPTION_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.EN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.IDENTIFIER_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ID_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LABELS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.LANGUAGE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.MAIN_TITLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NB_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.ONE;
import static no.sikt.nva.nvi.test.TestConstants.PAGES_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_CONTEXT_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_DATE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.PUBLICATION_INSTANCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.REFERENCE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.STATUS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TOP_LEVEL_ORGANIZATIONS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;

/**
 * This is intended to create a JSON representation of an expanded resource, mirroring the expected input documents from
 * `nva-publication-api`.
 */
@JacocoGenerated
public record SampleExpandedPublication(URI id, UUID identifier, String mainTitle, String issn,
                                        String publicationContextType,
                                        List<SampleExpandedPublicationChannel> publicationChannels,
                                        SampleExpandedPublicationDate publicationDate, String instanceType,
                                        String abstractText, String language, String status,
                                        List<SampleExpandedContributor> contributors) {

    private static final String TEMPLATE_JSON_PATH = "template_publication.json";
    private static final String PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY = "Publication channels must not be empty";


    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(SampleExpandedPublication other) {
        return new Builder(other);
    }

    public String toJsonString() {
        return toJsonNode().toString();
    }

    public JsonNode toJsonNode() {
        var root = objectMapper.createObjectNode();
        root.set(BODY_FIELD, createExpandedResource());
        return root;
    }

    private static ObjectNode createAndPopulatePublicationInstance(String type) {
        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put(TYPE_FIELD, type);

        switch (type) {
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

    private JsonNode createExpandedResource() {
        try (var content = IoUtils.inputStreamFromResources(TEMPLATE_JSON_PATH)) {
            var root = (ObjectNode) objectMapper.readTree(content);
            root.put(TYPE_FIELD, "Publication");
            root.put(ID_FIELD, id.toString());
            root.put(IDENTIFIER_FIELD, identifier.toString());
            root.put(STATUS_FIELD, status);

            root.set(ENTITY_DESCRIPTION_FIELD, createEntityDescriptionNode());
            root.set(TOP_LEVEL_ORGANIZATIONS_FIELD, createTopLevelOrganizationsNode());

            return root;
        } catch (Exception e) {
            throw new IllegalStateException("Template could not be read", e);
        }
    }

    private JsonNode createTopLevelOrganizationsNode() {
        var topLevelOrganizations = objectMapper.createArrayNode();
        contributors.stream()
                    .map(SampleExpandedContributor::affiliationIds)
                    .flatMap(List::stream)
                    .distinct()
                    .map(URI::toString)
                    .map(SampleExpandedPublication::createOrganizationNode)
                    .forEach(topLevelOrganizations::add);
        return topLevelOrganizations;
    }

    private ObjectNode createReferenceNode() {
        var referenceNode = objectMapper.createObjectNode();
        referenceNode.put(TYPE_FIELD, "Reference");
        referenceNode.set(PUBLICATION_INSTANCE_FIELD, createAndPopulatePublicationInstance(instanceType));

        var publicationContextNode = objectMapper.createObjectNode();
        if (publicationChannels.isEmpty()) {
            throw new IllegalArgumentException(PUBLICATION_CHANNELS_MUST_NOT_BE_EMPTY);
        } else if (publicationChannels.size() == ONE) {
            publicationContextNode = publicationChannels.getFirst()
                                                        .asObjectNode();
        } else {
            publicationContextNode.put(TYPE_FIELD, publicationContextType);
            for (SampleExpandedPublicationChannel publicationChannel : publicationChannels) {
                publicationContextNode.set(publicationChannel.type(), publicationChannel.asObjectNode());
            }
        }
        referenceNode.set(PUBLICATION_CONTEXT_FIELD, publicationContextNode);
        return referenceNode;
    }

    private ObjectNode createEntityDescriptionNode() {
        var entityDescriptionNode = objectMapper.createObjectNode();
        entityDescriptionNode.put(TYPE_FIELD, "EntityDescription");
        entityDescriptionNode.put(MAIN_TITLE_FIELD, mainTitle);
        if (nonNull(language)) {
            entityDescriptionNode.put(LANGUAGE_FIELD, language);
        }
        if (nonNull(abstractText)) {
            entityDescriptionNode.put(ABSTRACT_FIELD, abstractText);
        }
        entityDescriptionNode.set(CONTRIBUTORS_FIELD, createContributorsNode());
        entityDescriptionNode.set(PUBLICATION_DATE_FIELD, publicationDate.asObjectNode());
        entityDescriptionNode.set(REFERENCE_FIELD, createReferenceNode());
        return entityDescriptionNode;
    }

    private ArrayNode createContributorsNode() {
        var contributorsNode = objectMapper.createArrayNode();
        contributors.stream()
                    .map(SampleExpandedContributor::asObjectNode)
                    .forEach(contributorsNode::add);
        return contributorsNode;
    }

    @JacocoGenerated
    public static final class Builder {

        private URI id;
        private UUID identifier = UUID.randomUUID();
        private String mainTitle = randomString();
        private List<SampleExpandedContributor> contributors;
        private String status = "PUBLISHED";
        private String language;
        private String abstractText;
        private String publicationContextType = "Book";
        private String instanceType = "AcademicArticle";
        private List<SampleExpandedPublicationChannel> publicationChannels;
        private SampleExpandedPublicationDate publicationDate;
        private String issn;

        private Builder() {
        }

        private Builder(SampleExpandedPublication other) {
            this.id = other.id;
            this.identifier = other.identifier;
            this.mainTitle = other.mainTitle;
            this.contributors = other.contributors;
            this.status = other.status;
            this.language = other.language;
            this.abstractText = other.abstractText;
            this.publicationContextType = other.publicationContextType;
            this.instanceType = other.instanceType;
            this.publicationChannels = other.publicationChannels;
            this.publicationDate = other.publicationDate;
            this.issn = other.issn;
        }

        public Builder withContributors(List<SampleExpandedContributor> contributors) {
            this.contributors = contributors;
            return this;
        }

        public Builder withPublicationChannels(List<SampleExpandedPublicationChannel> publicationChannels) {
            this.publicationChannels = publicationChannels;
            return this;
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withPublicationDate(SampleExpandedPublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public SampleExpandedPublication build() {
            if (isNull(id)) {
                id = generatePublicationId(identifier);
            }
            return new SampleExpandedPublication(id,
                                                 identifier,
                                                 mainTitle,
                                                 issn,
                                                 publicationContextType,
                                                 publicationChannels,
                                                 publicationDate,
                                                 instanceType,
                                                 abstractText,
                                                 language,
                                                 status,
                                                 contributors);
        }
    }
}