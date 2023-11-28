package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.exception.UnauthorizedOperationException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import nva.commons.core.JacocoGenerated;

public class Note {

    private static final String DELETE_MESSAGE_ERROR = "Can not delete message you does not own!";
    private final CandidateRepository repository;
    private final UUID identifier;
    private final UUID noteId;
    private final Instant createdDate;
    private final Username user;
    private final String text;

    public Note(CandidateRepository repository, UUID identifier, NoteDao note) {
        this.repository = repository;
        this.identifier = identifier;
        this.noteId = note.note().noteId();
        this.createdDate = note.note().createdDate();
        this.user = Username.fromUserName(note.note().user());
        this.text = note.note().text();
    }

    public static Note fromRequest(CreateNoteRequest input, UUID candidateIdentifier,
                                   CandidateRepository repository) {
        validate(input);
        var noteDao = repository.saveNote(candidateIdentifier, DbNote.builder()
                                                                   .text(input.text())
                                                                   .user(dbUserName(input))
                                                                   .build());
        return new Note(repository, candidateIdentifier, noteDao);
    }

    public void validateOwner(String username) {
        if (isNotNoteOwner(username, user)) {
            throw new UnauthorizedOperationException(DELETE_MESSAGE_ERROR);
        }
    }

    public UUID getNoteId() {
        return noteId;
    }

    public void delete() {
        repository.deleteNote(identifier, noteId);
    }

    public NoteDto toDto() {
        return NoteDto.builder()
                   .withCreatedDate(createdDate)
                   .withUser(user.value())
                   .withText(text)
                   .withIdentifier(noteId)
                   .build();
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        int result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (noteId != null ? noteId.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (user != null ? user.hashCode() : 0);
        result = 31 * result + (text != null ? text.hashCode() : 0);
        return result;
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Note note = (Note) o;

        if (!Objects.equals(identifier, note.identifier)) {
            return false;
        }
        if (!Objects.equals(noteId, note.noteId)) {
            return false;
        }
        if (!Objects.equals(createdDate, note.createdDate)) {
            return false;
        }
        if (!Objects.equals(user, note.user)) {
            return false;
        }
        return Objects.equals(text, note.text);
    }

    private static boolean isNotNoteOwner(String username, Username user) {
        return !user.value().equals(username);
    }

    private static no.sikt.nva.nvi.common.db.model.Username dbUserName(CreateNoteRequest input) {
        return no.sikt.nva.nvi.common.db.model.Username.fromString(
            input.username());
    }

    private static void validate(CreateNoteRequest request) {
        if (isNull(request.text()) || request.text().isEmpty()
            || isNull(request.username()) || request.text().isEmpty()) {
            throw new IllegalArgumentException();
        }
    }
}
