package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import java.util.List;

public sealed interface NviCreator permits VerifiedNviCreator, UnverifiedNviCreator {

    List<NviOrganization> nviAffiliations();

    List<URI> nviAffiliationsIds();

    boolean isAffiliatedWith(URI institutionId);

    List<URI> getAffiliationsPartOf(URI institutionId);
}