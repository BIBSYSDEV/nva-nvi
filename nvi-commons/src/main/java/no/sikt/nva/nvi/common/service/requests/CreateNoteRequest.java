package no.sikt.nva.nvi.common.service.requests;

import java.util.UUID;

public interface CreateNoteRequest {

    UUID identifier();

    String text();

    String username();
}
