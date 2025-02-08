package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import no.sikt.nva.nvi.common.model.CreateNoteRequest;

public class RequestFixtures {

  public static CreateNoteRequest createNoteRequest(String text, String username) {
    return new CreateNoteRequest(text, username, randomUri());
  }
}
