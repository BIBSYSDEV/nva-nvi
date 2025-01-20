package no.sikt.nva.nvi.events.cristin;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record InstitutionPoints(
    URI institutionId, BigDecimal points, List<CreatorPoints> creatorPoints) {

  public record CreatorPoints(URI creatorId, URI affiliationId, BigDecimal points) {}
}
