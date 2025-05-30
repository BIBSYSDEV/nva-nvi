package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;

// TODO: Merge these two records into one and make VerificationStatus a field?
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "UnverifiedNviCreator", value = UnverifiedNviCreatorDto.class),
  @JsonSubTypes.Type(name = "VerifiedNviCreator", value = VerifiedNviCreatorDto.class)
})
public sealed interface NviCreatorDto permits VerifiedNviCreatorDto, UnverifiedNviCreatorDto {

  String name();

  List<URI> affiliations();

  DbCreatorType toDao();
}
