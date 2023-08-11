package no.sikt.nva.nvi.common.model;

import java.net.URI;
import java.util.List;

public record VerifiedCreator(URI creatorId, List<Institution> affiliations) {

}
