package no.sikt.nva.nvi.common.service.model;

import static java.util.UUID.randomUUID;
import static nva.commons.core.StringUtils.isBlank;

import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.exception.UnauthorizedOperationException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;

public record Note(
    UUID candidateIdentifier,
    UUID noteIdentifier,
    Instant createdDate,
    Username user,
    String text) {

  public static Note fromDao(NoteDao note) {
    var user = Username.fromUserName(note.note().user());
    return new Note(
        note.identifier(),
        note.note().noteId(),
        note.note().createdDate(),
        user,
        note.note().text());
  }

  public static Note fromRequest(CreateNoteRequest input, UUID candidateIdentifier) {
    validate(input);
    return new Note(
        candidateIdentifier,
        randomUUID(),
        Instant.now(),
        Username.fromString(input.username()),
        input.text());
  }

  public NoteDao toDao() {
    var dbNote =
        DbNote.builder()
            .noteId(noteIdentifier)
            .createdDate(createdDate)
            .user(no.sikt.nva.nvi.common.db.model.Username.fromString(user.value()))
            .text(text)
            .build();
    return NoteDao.builder().identifier(candidateIdentifier).note(dbNote).build();
  }

  public NoteDto toDto() {
    return NoteDto.builder()
        .withCreatedDate(createdDate)
        .withUser(user.value())
        .withText(text)
        .withIdentifier(noteIdentifier)
        .build();
  }

  public void validateOwner(String username) {
    if (isNotNoteOwner(username, user)) {
      throw new UnauthorizedOperationException("Cannot delete a note created by another user");
    }
  }

  private static boolean isNotNoteOwner(String username, Username user) {
    return !user.value().equals(username);
  }

  private static void validate(CreateNoteRequest request) {
    if (isBlank(request.text()) || isBlank(request.username())) {
      throw new IllegalArgumentException();
    }
  }
}
