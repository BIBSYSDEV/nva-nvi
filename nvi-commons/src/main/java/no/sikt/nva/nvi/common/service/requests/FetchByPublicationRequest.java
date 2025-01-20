package no.sikt.nva.nvi.common.service.requests;

import java.net.URI;

@FunctionalInterface
public interface FetchByPublicationRequest {

  URI publicationId();
}
