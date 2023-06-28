package no.sikt.nva.nvi.index.utils;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import nva.commons.core.paths.UriWrapper;

public class ExpandedResourceGenerator {

    public static String createExpandedResource(NviCandidateIndexDocument document, String host) {
        var root = objectMapper.createObjectNode();

        root.put("id", UriWrapper.fromUri(host).addChild(document.getIdentifier()).toString());

        var entityDescription = objectMapper.createObjectNode();

        var contributors = populateAndCreateContributors(document);

        entityDescription.set("contributors", contributors);

        entityDescription.put("mainTitle", document.getPublication().getTitle());

        var publicationDate = createAndPopulatePublicationDate(document);

        entityDescription.set("publicationDate", publicationDate);

        var reference = objectMapper.createObjectNode();

        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", document.getPublication().getType());

        reference.set("publicationInstance", publicationInstance);

        entityDescription.set("reference", reference);

        root.set("entityDescription", entityDescription);

        root.put("identifier", document.getIdentifier());

        return attempt(() -> objectMapper.writeValueAsString(root)).orElseThrow();
    }

    private static String extractMonth(String dateString) {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.valueOf(LocalDate.parse(dateString, formatter).getMonthValue());
    }

    private static String extractDay(String dateString) {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.valueOf(LocalDate.parse(dateString, formatter).getDayOfMonth());
    }

    private static String extractYear(String dateString) {
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.valueOf(LocalDate.parse(dateString, formatter).getYear());
    }

    private static ObjectNode createAndPopulatePublicationDate(NviCandidateIndexDocument document) {
        var publicationDate = objectMapper.createObjectNode();
        publicationDate.put("type", "PublicationDate");
        if (document.getPublication().getPublicationDate().length() == 4) {
            publicationDate.put("year", document.getPublication().getPublicationDate());
        } else if (document.getPublication().getPublicationDate().length() > 4) {
            publicationDate.put("day", extractDay(document.getPublication().getPublicationDate()));
            publicationDate.put("month", extractMonth(document.getPublication().getPublicationDate()));
            publicationDate.put("year", extractYear(document.getPublication().getPublicationDate()));
        }
        return publicationDate;
    }

    private static ArrayNode populateAndCreateContributors(NviCandidateIndexDocument document) {

        var contributors = objectMapper.createArrayNode();
        document.getPublication().getContributors().forEach(contributor -> {

            var contributorNode = objectMapper.createObjectNode();

            contributorNode.put("type", "Contributor");

            var affiliations = createAndPopulateAffiliationsNode(document);

            contributorNode.set("affiliations", affiliations);

            var identity = objectMapper.createObjectNode();
            identity.put("id", contributor.id());
            identity.put("name", contributor.name());
            identity.put("orcid", contributor.orcId());

            contributorNode.set("identity", identity);

            contributors.add(contributorNode);
        });

        return contributors;
    }

    private static ArrayNode createAndPopulateAffiliationsNode(NviCandidateIndexDocument document) {
        var affiliations = objectMapper.createArrayNode();

        document.getAffiliations().forEach(affiliation -> {
            var affiliationNode = objectMapper.createObjectNode();
            affiliationNode.put("id", affiliation.getId());
            affiliationNode.put("type", "Organization");
            var labels = objectMapper.createObjectNode();

            labels.put("nb", affiliation.getLabels().get("nb"));
            labels.put("en", affiliation.getLabels().get("en"));

            affiliationNode.set("labels", labels);

            affiliations.add(affiliationNode);
        });
        return affiliations;
    }
}
