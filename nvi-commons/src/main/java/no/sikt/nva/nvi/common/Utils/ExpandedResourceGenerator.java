package no.sikt.nva.nvi.common.Utils;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.paths.UriWrapper;

public class ExpandedResourceGenerator {

    public static final ObjectMapper objectMapper = JsonUtils.dtoObjectMapper;

    public static String createExpandedResource(Candidate candidate) {
        var root = objectMapper.createObjectNode();

        root.put("id", candidate.publicationId().toString());

        var entityDescription = objectMapper.createObjectNode();

        var contributors = objectMapper.createArrayNode();
        candidate.creators().forEach(contributor -> {

            var contributorNode = objectMapper.createObjectNode();

            contributorNode.put("type", "Contributor");

            var affiliations = objectMapper.createArrayNode();

            contributor.affiliations().forEach(affiliation -> {
                var affiliationNode = objectMapper.createObjectNode();
                affiliationNode.put("id", affiliation.id().toString());
                affiliationNode.put("type", "Organization");

                affiliations.add(affiliationNode);
            });

            contributorNode.set("affiliations", affiliations);

            contributors.add(contributorNode);
        });

        entityDescription.set("contributors", contributors);

        var publicationDate = objectMapper.createObjectNode();
        publicationDate.put("type", "PublicationDate");
        publicationDate.put("day", candidate.publicationDate().day());
        publicationDate.put("month", candidate.publicationDate().month());
        publicationDate.put("year", candidate.publicationDate().year());

        entityDescription.set("publicationDate", publicationDate);

        var reference = objectMapper.createObjectNode();

        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", candidate.instanceType());

        reference.set("publicationInstance", publicationInstance);

        entityDescription.set("reference", reference);

        root.set("entityDescription", entityDescription);

        root.put("identifier", extractPublicationIdentifier(candidate.publicationId()));

        var body = objectMapper.createObjectNode();
        body.set("body", root);

        return attempt(() -> objectMapper.writeValueAsString(body)).orElseThrow();
    }

    private static String extractPublicationIdentifier(URI publicationId) {
        return UriWrapper.fromUri(publicationId).getLastPathElement();
    }

}
