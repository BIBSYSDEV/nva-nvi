package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.db.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;

public class RequestFixtures {

  public static CreateNoteRequest createNoteRequest(String text, String username) {
    return new CreateNoteRequest(text, username, randomUri());
  }

  public static CreateNoteRequest randomNoteRequest() {
    var text = randomString();
    var username = randomUsername().toString();
    return new CreateNoteRequest(text, username, randomOrganizationId());
  }

  public static UpdateAssigneeRequest randomAssigneeRequest() {
    var username = randomUsername().toString();
    return new UpdateAssigneeRequest(randomOrganizationId(), username);
  }
}
