package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;

public sealed interface NviCreator permits VerifiedNviCreator, UnverifiedNviCreator {

    List<NviOrganization> nviAffiliations();

    List<URI> nviAffiliationsIds();

    boolean isAffiliatedWith(URI institutionId);
}