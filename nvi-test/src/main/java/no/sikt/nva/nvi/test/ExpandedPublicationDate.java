package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public record ExpandedPublicationDate(String year, String month, String day) {

    public ObjectNode asObjectNode() {
        var publicationDateNode = objectMapper.createObjectNode();
        publicationDateNode.put(TYPE_FIELD, "PublicationDate");
        publicationDateNode.put("year", year);
        if (nonNull(month)) {
            publicationDateNode.put("month", month);
        }
        if (nonNull(day)) {
            publicationDateNode.put("day", day);
        }
        return publicationDateNode;
    }
}
