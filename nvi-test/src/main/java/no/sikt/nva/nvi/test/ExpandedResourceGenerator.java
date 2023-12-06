package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;

public final class ExpandedResourceGenerator {

    private ExpandedResourceGenerator() {
    }

    //TODO: To be used in new tests for new IndexDocumentHandler
    public static String createExpandedResource(Candidate candidate) {
        var root = objectMapper.createObjectNode();

        root.put("id", candidate.getPublicationDetails().publicationId().toString());

        var entityDescription = objectMapper.createObjectNode();

        var contributors = populateAndCreateContributors(candidate);

        entityDescription.set("contributors", contributors);

        entityDescription.put("mainTitle", randomString());

        var publicationDate = createAndPopulatePublicationDate(candidate.getPublicationDetails().publicationDate());

        entityDescription.set("publicationDate", publicationDate);

        var reference = objectMapper.createObjectNode();

        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", candidate.getPublicationDetails().type());

        reference.set("publicationInstance", publicationInstance);

        entityDescription.set("reference", reference);

        root.set("entityDescription", entityDescription);

        root.put("identifier", candidate.getIdentifier().toString());

        return attempt(() -> objectMapper.writeValueAsString(root)).orElseThrow();
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

    private static ArrayNode populateAndCreateContributors(Candidate candidate) {

        var contributors = objectMapper.createArrayNode();
        var creators = candidate.getPublicationDetails().creators();
        creators.forEach(creator -> {

            var contributorNode = objectMapper.createObjectNode();

            contributorNode.put("type", "Contributor");

            var affiliations = createAndPopulateAffiliationsNode(creator.affiliations());

            contributorNode.set("affiliations", affiliations);

            var identity = objectMapper.createObjectNode();
            identity.put("id", creator.id().toString());
            identity.put("name", randomString());
            identity.put("orcid", randomString());

            contributorNode.set("identity", identity);

            contributors.add(contributorNode);
        });

        return contributors;
    }

    private static ArrayNode createAndPopulateAffiliationsNode(List<URI> creatorAffiliations) {
        var affiliations = objectMapper.createArrayNode();

        creatorAffiliations.forEach(affiliation -> {
            var affiliationNode = objectMapper.createObjectNode();
            affiliationNode.put("id", affiliation.toString());
            affiliationNode.put("type", "Organization");
            var labels = objectMapper.createObjectNode();

            labels.put("nb", randomString());
            labels.put("en", randomString());

            affiliationNode.set("labels", labels);

            affiliations.add(affiliationNode);
        });
        return affiliations;
    }
}
