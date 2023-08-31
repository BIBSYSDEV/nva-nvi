package no.sikt.nva.nvi.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import no.sikt.nva.nvi.common.model.business.Username;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Note(Username user,
                   String text,
                   Instant createdDate) {
}
