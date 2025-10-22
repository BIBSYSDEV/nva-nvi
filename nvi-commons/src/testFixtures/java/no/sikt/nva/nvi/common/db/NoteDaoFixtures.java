package no.sikt.nva.nvi.common.db;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomDbUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.util.UUID;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;

public class NoteDaoFixtures {

  public static NoteDao randomNoteDao() {
    return randomNoteDao(randomUUID());
  }

  public static NoteDao randomNoteDao(UUID candidateIdentifier) {
    return new NoteDao(
        candidateIdentifier,
        new DbNote(randomUUID(), randomDbUsername(), randomString(), randomInstant()),
        randomUUID().toString());
  }
}
