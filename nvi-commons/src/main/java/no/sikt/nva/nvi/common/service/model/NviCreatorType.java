package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import java.util.List;

@Deprecated
// This overlaps with NviCreatorType defined in nvi-commons and should be removed (with the implementations).
public sealed interface NviCreatorType permits VerifiedNviCreator, UnverifiedNviCreator {

    List<URI> nviAffiliationsIds();

    boolean isAffiliatedWith(URI institutionId);

    List<URI> getAffiliationsPartOf(URI institutionId);
}