package no.sikt.nva.nvi.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JacocoGenerated // TODO not in use yet
public record Note(DbUsername user,
                   String text,
                   Instant createdDate) {
}
