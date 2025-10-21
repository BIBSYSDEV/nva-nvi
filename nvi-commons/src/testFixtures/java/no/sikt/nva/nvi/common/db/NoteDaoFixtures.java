package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomDbUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.util.UUID;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;

public class NoteDaoFixtures {

  public static NoteDao randomNoteDao() {
    return new NoteDao(
        UUID.randomUUID(),
        new DbNote(UUID.randomUUID(), randomDbUsername(), randomString(), randomInstant()),
        UUID.randomUUID().toString());
  }
}
