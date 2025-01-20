package no.sikt.nva.nvi.common.model;

import java.net.URI;

public record CreateNoteRequest(String text, String username, URI institutionId)
    implements no.sikt.nva.nvi.common.service.requests.CreateNoteRequest {}
