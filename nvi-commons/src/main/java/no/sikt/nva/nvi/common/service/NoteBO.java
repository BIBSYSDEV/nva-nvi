package no.sikt.nva.nvi.common.service;

import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;

public class NoteBO {

    private final CandidateRepository repository;
    private final UUID identifier;
    private final NoteDao original;

    public NoteBO(CandidateRepository repository, UUID identifier, NoteDao note) {
        this.repository = repository;
        this.identifier = identifier;
        this.original = note;
    }

    public static NoteBO fromRequest(UUID candidateIdentifier, CandidateRepository repository,
                                     CreateNoteRequest input) {
        var noteDao = repository.saveNote(candidateIdentifier, DbNote.builder()
                                                                   .text(input.text())
                                                                   .user(Username.fromString(input.username()))
                                                                   .build());
        return new NoteBO(repository, candidateIdentifier, noteDao);
    }

    public UUID id() {
        return original.note().noteId();
    }

    public NoteDao note() {
        return original;
    }

    public void delete() {
        repository.deleteNote(identifier, original.note().noteId());
    }
}
