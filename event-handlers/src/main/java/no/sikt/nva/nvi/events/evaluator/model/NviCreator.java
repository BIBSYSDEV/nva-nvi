package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;

// FIXME: This interface and its implementations should be merged with the NviCreator model in the
// common module.
public sealed interface NviCreator permits VerifiedNviCreator, UnverifiedNviCreator {

  List<NviOrganization> nviAffiliations();

  List<URI> nviAffiliationsIds();

  boolean isAffiliatedWith(URI institutionId);

  NviCreatorDto toDto();
}
