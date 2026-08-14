package no.sikt.nva.nvi.index.model;

import static no.sikt.nva.nvi.index.aws.S3StorageWriter.GZIP_ENDING;

import java.net.URI;
import java.util.UUID;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.paths.UriWrapper;

public record PersistedIndexDocumentMessage(URI documentUri) implements JsonSerializable {

  public static UUID candidateIdentifierFrom(URI documentUri) {
    var filename = UriWrapper.fromUri(documentUri).getPath().getLastPathElement();
    return UUID.fromString(filename.replace(GZIP_ENDING, ""));
  }
}
