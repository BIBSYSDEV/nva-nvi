package no.sikt.nva.nvi.rest.fetch;

public record StatusWithDescriptionDto(String status, String description) {

  public static StatusWithDescriptionDto fromStatus(StatusDto status) {
    return new StatusWithDescriptionDto(status.toString(), status.getDescription());
  }
}
