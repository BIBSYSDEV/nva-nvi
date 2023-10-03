package no.sikt.nva.nvi.common.model;

public record CreateNoteRequest(String text, String username)
    implements no.sikt.nva.nvi.common.service.requests.CreateNoteRequest {

    @Override
    public String text() {
        return text;
    }

    @Override
    public String username() {
        return username;
    }
}
