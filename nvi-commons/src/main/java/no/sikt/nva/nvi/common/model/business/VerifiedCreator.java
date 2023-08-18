package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;

public record VerifiedCreator(URI creatorId, List<URI> affiliations) {

}
