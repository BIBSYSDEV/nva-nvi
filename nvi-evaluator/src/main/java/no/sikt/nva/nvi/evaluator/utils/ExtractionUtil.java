package no.sikt.nva.nvi.evaluator.utils;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;

public final class ExtractionUtil {

    public ExtractionUtil() {
    }

    public static URI extractId(JsonNode jsonNode) {
        return URI.create(extractJsonNodeTextValue(jsonNode, JSON_PTR_ID));
    }
}
