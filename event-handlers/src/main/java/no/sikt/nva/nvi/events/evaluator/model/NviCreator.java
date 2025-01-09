package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.NviOrganization;

public sealed interface NviCreator permits VerifiedNviCreator, UnverifiedNviCreator {

    List<NviOrganization> nviAffiliations();

    List<URI> nviAffiliationsIds();

    boolean isAffiliatedWith(URI institutionId);

    List<URI> getAffiliationsPartOf(URI institutionId);
}