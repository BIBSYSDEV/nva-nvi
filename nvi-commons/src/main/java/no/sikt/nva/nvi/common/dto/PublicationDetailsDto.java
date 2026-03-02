package no.sikt.nva.nvi.common.dto;

import static java.util.Objects.requireNonNull;
import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeNull;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;

public record PublicationDetailsDto(
    URI id,
    String identifier,
    String title,
    String status,
    String language,
    String abstractText,
    PageCountDto pageCount,
    PublicationDateDto publicationDate,
    boolean isApplicable,
    int creatorCount,
    Instant modifiedDate,
    Collection<String> handles) {

  public PublicationDetailsDto {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(status, "Required field 'status' is null");
    handles = copyOfNullable(handles);
  }

  public void validate() {
    shouldNotBeNull(publicationDate, "Required field 'publicationDate' is null");
  }

  public static PublicationDetailsDto fromPublicationDto(PublicationDto publication) {
    return new PublicationDetailsDto(
        publication.id(),
        publication.identifier(),
        publication.title(),
        publication.status(),
        publication.language(),
        publication.abstractText(),
        publication.pageCount(),
        publication.publicationDate(),
        publication.isApplicable(),
        publication.contributors().size(),
        publication.modifiedDate(),
        publication.handles());
  }
}
