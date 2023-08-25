package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;

public record Creator(URI creatorId, List<URI> affiliations) {
}
