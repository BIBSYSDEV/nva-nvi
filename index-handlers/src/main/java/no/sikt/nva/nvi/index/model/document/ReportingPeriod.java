package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type")
public record ReportingPeriod(String year) {

}
