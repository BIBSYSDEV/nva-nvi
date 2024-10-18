package no.sikt.nva.nvi.test;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.Creator;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.utils.JsonUtils;

public final class ExpandedResourceGenerator {

    public static final String HARDCODED_NORWEGIAN_LABEL = "Hardcoded Norwegian label";
    public static final String HARDCODED_ENGLISH_LABEL = "Hardcoded English label";
    public static final String NB_FIELD = "nb";
    public static final String EN_FIELD = "en";
    private final boolean populateLanguage;
    private final boolean populateIssn;
    private final boolean populateAbstract;
    private final Candidate candidate;

    private ExpandedResourceGenerator(boolean populateLanguage, boolean populateIssn, boolean populateAbstract,
                                      Candidate candidate) {
        this.populateLanguage = populateLanguage;
        this.populateIssn = populateIssn;
        this.populateAbstract = populateAbstract;
        this.candidate = candidate;
    }

    public static String extractOptionalAbstract(JsonNode expandedResource) {
        return JsonUtils.extractOptJsonNodeTextValue(expandedResource, "/entityDescription/abstract").orElse(null);
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
        return JsonUtils.extractJsonNodeTextValue(expandedResource,
                                                  "/entityDescription/reference/publicationInstance/type");
    }

    public static Builder builder() {
        return new Builder();
    }

    public JsonNode createExpandedResource() {
        return createExpandedResource(Collections.emptyList());
    }

    public JsonNode createExpandedResource(List<URI> nonNviContributorAffiliationIds) {
        var root = objectMapper.createObjectNode();

        root.put("id", candidate.getPublicationId().toString());

        var entityDescription = objectMapper.createObjectNode();

        var contributors = populateAndCreateContributors(candidate, nonNviContributorAffiliationIds);

        entityDescription.set("contributors", contributors);

        entityDescription.put("mainTitle", randomString());

        var publicationDate = createAndPopulatePublicationDate(candidate.getPublicationDetails().publicationDate());

        entityDescription.set("publicationDate", publicationDate);

        var reference = objectMapper.createObjectNode();

        var publicationInstance = createAndPopulatePublicationInstance(candidate);
        reference.set("publicationInstance", publicationInstance);

        var publicationContext = createAndPopulatePublicationContext(candidate, populateIssn);
        reference.set("publicationContext", publicationContext);

        entityDescription.set("reference", reference);
        if (populateLanguage) {
            entityDescription.put("language", "http://lexvo.org/id/iso639-3/nob");
        }

        if (populateAbstract) {
            entityDescription.put("abstract", randomString());
        }

        root.set("entityDescription", entityDescription);

        root.put("identifier", candidate.getIdentifier().toString());

        var topLevelOrganizations = createAndPopulateTopLevelOrganizations(candidate);

        root.set("topLevelOrganizations", topLevelOrganizations);

        return root;
    }

    private static ObjectNode createAndPopulatePublicationInstance(Candidate candidate) {
        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", candidate.getPublicationDetails().type());
        switch (candidate.getPublicationDetails().type()) {
            case "AcademicArticle", "AcademicLiteratureReview", "AcademicChapter" -> {
                var pages = objectMapper.createObjectNode();
                pages.put("begin", "pageBegin");
                pages.put("end", "pageEnd");
                publicationInstance.set("pages", pages);
            }
            case "AcademicMonograph" -> {
                var pages = objectMapper.createObjectNode();
                pages.put("pages", "numberOfPages");
                publicationInstance.set("pages", pages);
            }
        }
        return publicationInstance;
    }

    private static ObjectNode createAndPopulatePublicationContext(Candidate candidate, boolean populateIssn) {
        if (isNull(candidate.getPublicationDetails().channelType())) {
            return objectMapper.createObjectNode();
        }
        return switch (candidate.getPublicationDetails().channelType()) {
            case JOURNAL -> createJournalPublicationContext(candidate, populateIssn);
            case SERIES -> createSeriesPublicationContext(candidate, populateIssn);
            case PUBLISHER -> createPublisherPublicationContext(candidate);
        };
    }

    private static ObjectNode createPublisherPublicationContext(Candidate candidate) {
        var publisher = objectMapper.createObjectNode();
        publisher.put("type", "Publisher");
        publisher.put("id", candidate.getPublicationDetails().publicationChannelId().toString());
        publisher.put("level", candidate.getPublicationDetails().level());
        publisher.put("name", randomString());
        var publicationContext = objectMapper.createObjectNode();
        publicationContext.set("publisher", publisher);
        return publicationContext;
    }

    private static ObjectNode createSeriesPublicationContext(Candidate candidate, boolean populateIssn) {
        var series = objectMapper.createObjectNode();
        series.put("type", "Series");
        series.put("id", candidate.getPublicationDetails().publicationChannelId().toString());
        series.put("level", candidate.getPublicationDetails().level());
        series.put("name", randomString());
        if (populateIssn) {
            series.put("printIssn", randomString());
        }
        var publicationContext = objectMapper.createObjectNode();
        publicationContext.set("series", series);
        return publicationContext;
    }

    private static ObjectNode createJournalPublicationContext(Candidate candidate, boolean populateIssn) {
        var journal = objectMapper.createObjectNode();
        journal.put("type", "Journal");
        journal.put("id", candidate.getPublicationDetails().publicationChannelId().toString());
        journal.put("level", candidate.getPublicationDetails().level());
        journal.put("name", randomString());
        if (populateIssn) {
            journal.put("printIssn", randomString());
        }
        return journal;
    }

    private static JsonNode createAndPopulateTopLevelOrganizations(Candidate candidate) {
        var topLevelOrganizations = objectMapper.createArrayNode();
        candidate.getPublicationDetails().creators().stream()
            .map(Creator::affiliations)
            .flatMap(List::stream)
            .distinct()
            .map(URI::toString)
            .map(ExpandedResourceGenerator::createOrganizationNode)
            .forEach(topLevelOrganizations::add);
        return topLevelOrganizations;
    }

    private static JsonNode createOrganizationNode(String affiliationId) {
        var organization = objectMapper.createObjectNode();
        organization.put("id", affiliationId);
        organization.put("type", "Organization");
        var labels = objectMapper.createObjectNode();
        labels.put(NB_FIELD, HARDCODED_NORWEGIAN_LABEL);
        labels.put(EN_FIELD, HARDCODED_ENGLISH_LABEL);
        organization.set("labels", labels);
        return organization;
    }

    private static ObjectNode createAndPopulatePublicationDate(PublicationDate date) {
        var publicationDate = objectMapper.createObjectNode();
        publicationDate.put("type", "PublicationDate");
        if (nonNull(date.day())) {
            publicationDate.put("day", date.day());
        }
        if (nonNull(date.month())) {
            publicationDate.put("month", date.month());
        }
        publicationDate.put("year", date.year());
        return publicationDate;
    }

    private static ArrayNode populateAndCreateContributors(Candidate candidate,
                                                           List<URI> nonNviContributorAffiliationIds) {

        var contributors = objectMapper.createArrayNode();
        var creators = candidate.getPublicationDetails().creators();
        creators.stream()
            .map(creator -> createContributorNode(creator.affiliations(), creator.id()))
            .forEach(contributors::add);
        addOtherRandomContributors(contributors, nonNviContributorAffiliationIds);
        return contributors;
    }

    private static void addOtherRandomContributors(ArrayNode contributors, List<URI> affiliationsIds) {
        IntStream.range(0, 10).mapToObj(i -> createContributorNode(affiliationsIds, URI.create(randomString())))
            .forEach(contributors::add);
    }

    private static ObjectNode createContributorNode(List<URI> affiliationsUris, URI contributorId) {
        var contributorNode = objectMapper.createObjectNode();

        contributorNode.put("type", "Contributor");

        var affiliations = createAndPopulateAffiliationsNode(affiliationsUris);

        contributorNode.set("affiliations", affiliations);

        var role = objectMapper.createObjectNode();
        role.put("type", randomString());
        contributorNode.set("role", role);

        var identity = objectMapper.createObjectNode();
        identity.put("id", contributorId.toString());
        identity.put("name", randomString());
        identity.put("orcid", randomString());

        contributorNode.set("identity", identity);
        return contributorNode;
    }

    private static ArrayNode createAndPopulateAffiliationsNode(List<URI> creatorAffiliations) {
        var affiliations = objectMapper.createArrayNode();

        creatorAffiliations.forEach(affiliation -> {
            var affiliationNode = objectMapper.createObjectNode();
            affiliationNode.put("id", affiliation.toString());
            affiliationNode.put("type", "Organization");
            var labels = objectMapper.createObjectNode();

            labels.put(NB_FIELD, HARDCODED_NORWEGIAN_LABEL);
            labels.put(EN_FIELD, HARDCODED_ENGLISH_LABEL);

            affiliationNode.set("labels", labels);

            affiliations.add(affiliationNode);
        });
        return affiliations;
    }

    public static final class Builder {

        private boolean populateLanguage = false;
        private boolean populateIssn = false;
        private boolean populateAbstract = false;

        private Candidate candidate;

        private Builder() {
        }

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
            return new ExpandedResourceGenerator(populateLanguage, populateIssn, populateAbstract, candidate);
        }
    }
}
