package no.sikt.nva.nvi.events.model;

import java.net.URI;
import java.util.List;

public record UnverifiedNviCreator(String name, List<URI> nviAffiliations) {

}
