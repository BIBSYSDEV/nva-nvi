package no.sikt.nva.nvi.common.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonSerialize
public record CandidateDetailsDto(@JsonProperty("publicationId") URI publicationId,
                                  @JsonProperty("instanceType") String instanceType,
                                  @JsonProperty("level") String level,
                                  @JsonProperty("publicationDate") PublicationDateDto publicationDateDto,
                                  @JsonProperty("verifiedCreators") List<VerifiedCreatorDto> verifiedCreatorDtos) {

}
