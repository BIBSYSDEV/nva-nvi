package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import no.sikt.nva.nvi.common.client.model.Organization;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record ExpandedPublication(
    URI id,
    String publicationTitle,
    PublicationDate publicationDate,
    String publicationStatus,
    String publicationType,
    String publicationLanguage,
    boolean isInternationalCollaboration,
    Collection<PublicationChannel> publicationChannels,
    Collection<Contributor> contributors,
    Collection<Organization> topLevelOrganizations,
    Instant modifiedDate) {

  public ExpandedPublication {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(publicationDate, "Required field 'publicationDate' is null");
    requireNonNull(publicationStatus, "Required field 'publicationStatus' is null");
    requireNonNull(publicationType, "Required field 'publicationType' is null");
    requireNonNull(publicationChannels, "Required field 'publicationChannels' is null");
    requireNonNull(contributors, "Required field 'contributors' is null");
    requireNonNull(topLevelOrganizations, "Required field 'topLevelOrganizations' is null");
  }

  public static ExpandedPublication from(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, ExpandedPublication.class);
  }
}
