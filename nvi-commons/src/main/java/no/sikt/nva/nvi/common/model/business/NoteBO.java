package no.sikt.nva.nvi.common.model.business;

import static java.util.Objects.isNull;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;

public class NoteBO {

    private final CandidateRepository repository;
    private final UUID identifier;
    private final NoteDao dao;

    public NoteBO(CandidateRepository repository, UUID identifier, NoteDao note) {
        this.repository = repository;
        this.identifier = identifier;
        this.dao = note;
    }

    public static NoteBO fromRequest(CreateNoteRequest input, UUID candidateIdentifier,
                                     CandidateRepository repository) {
        validate(input);
        var noteDao = repository.saveNote(candidateIdentifier, DbNote.builder()
                                                                   .text(input.text())
                                                                   .user(Username.fromString(input.username()))
                                                                   .build());
        return new NoteBO(repository, candidateIdentifier, noteDao);
    }

    public UUID noteId() {
        return dao.note().noteId();
    }

    public void delete() {
        repository.deleteNote(identifier, dao.note().noteId());
    }

    public NoteDto toDto() {
        return NoteDto.builder()
                   .withCreatedDate(dao.note().createdDate())
                   .withUser(dao.note().user().value())
                   .withText(dao.note().text())
                   .withIdentifier(dao.note().noteId())
                   .build();
    }

    public NoteDao getDao() {
        return dao;
    }

    private static void validate(CreateNoteRequest request) {
        if (isNull(request.text()) || request.text().isEmpty()
            || isNull(request.username()) || request.text().isEmpty()) {
            throw new IllegalArgumentException();
        }
    }
}
