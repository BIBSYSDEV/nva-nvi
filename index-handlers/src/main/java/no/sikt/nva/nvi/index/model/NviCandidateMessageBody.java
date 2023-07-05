package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviCandidateMessageBody(String publicationId,
                                      List<String> affiliationApprovals) implements
                                                                         JsonSerializable {

}
