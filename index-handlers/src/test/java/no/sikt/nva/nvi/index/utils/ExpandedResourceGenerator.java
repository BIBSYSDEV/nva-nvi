package no.sikt.nva.nvi.index.utils;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;

public class ExpandedResourceGenerator {

    public static String createExpandedResource(NviCandidateIndexDocument document) {
        var root = objectMapper.createObjectNode();

        root.put("id", document.publicationDetails().id());

        var entityDescription = objectMapper.createObjectNode();

        var contributors = populateAndCreateContributors(document);

        entityDescription.set("contributors", contributors);

        entityDescription.put("mainTitle", document.publicationDetails().title());

        var publicationDate = createAndPopulatePublicationDate(document);

        entityDescription.set("publicationDate", publicationDate);

        var reference = objectMapper.createObjectNode();

        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", document.publicationDetails().type());

        reference.set("publicationInstance", publicationInstance);

        entityDescription.set("reference", reference);

        root.set("entityDescription", entityDescription);

        root.put("identifier", document.identifier());

        var body = objectMapper.createObjectNode();
        body.set("body", root);

        return attempt(() -> objectMapper.writeValueAsString(body)).orElseThrow();
    }

    private static ObjectNode createAndPopulatePublicationDate(NviCandidateIndexDocument document) {
        var publicationDate = objectMapper.createObjectNode();
        publicationDate.put("type", "PublicationDate");
        publicationDate.put("year", document.publicationDetails().publicationDate().year());
        publicationDate.put("day", document.publicationDetails().publicationDate().day());
        publicationDate.put("month", document.publicationDetails().publicationDate().month());
        return publicationDate;
    }

    private static ArrayNode populateAndCreateContributors(NviCandidateIndexDocument document) {

        var contributors = objectMapper.createArrayNode();
        document.publicationDetails().contributors().forEach(contributor -> {

            var contributorNode = objectMapper.createObjectNode();

            contributorNode.put("type", "Contributor");

            var affiliations = createAndPopulateAffiliationsNode(document);

            contributorNode.set("affiliations", affiliations);

            var identity = objectMapper.createObjectNode();
            identity.put("id", contributor.id());
            identity.put("name", contributor.name());
            identity.put("orcid", contributor.orcid());

            contributorNode.set("identity", identity);

            contributors.add(contributorNode);
        });

        return contributors;
    }

    private static ArrayNode createAndPopulateAffiliationsNode(NviCandidateIndexDocument document) {
        var affiliations = objectMapper.createArrayNode();

        document.approvals().forEach(affiliation -> {
            var affiliationNode = objectMapper.createObjectNode();
            affiliationNode.put("id", affiliation.id());
            affiliationNode.put("type", "Organization");
            var labels = objectMapper.createObjectNode();

            labels.put("nb", affiliation.labels().get("nb"));
            labels.put("en", affiliation.labels().get("en"));

            affiliationNode.set("labels", labels);

            affiliations.add(affiliationNode);
        });
        return affiliations;
    }
}
