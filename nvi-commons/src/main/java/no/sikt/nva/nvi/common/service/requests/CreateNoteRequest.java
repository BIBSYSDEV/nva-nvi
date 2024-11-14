package no.sikt.nva.nvi.common.service.requests;

import java.net.URI;

public interface CreateNoteRequest {

    String text();

    String username();

    URI institutionId();
}
