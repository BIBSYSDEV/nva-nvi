package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.RequestFixtures.createNoteRequest;
import static no.sikt.nva.nvi.common.RequestFixtures.randomNoteRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.dto.NoteDto;
import no.sikt.nva.nvi.common.service.exception.UnauthorizedOperationException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CandidateNotesTest extends CandidateTestSetup {

  @Test
  void shouldCreateNoteWhenValidCreateNoteRequest() {
    var candidate = createCandidate();
    var noteRequest = createNoteRequest(randomString(), randomString());
    candidateService.createNote(candidate, noteRequest);
    var userOrganizationId = getAnyOrganizationId(candidate);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var updatedCandidate = candidateService.getByIdentifier(candidate.identifier());
    var actualNote = getAnyNote(updatedCandidate);

    assertThat(noteRequest.username(), is(equalTo(actualNote.user())));
    assertThat(noteRequest.text(), is(equalTo(actualNote.text())));
    assertThat(actualNote.createdDate(), is(notNullValue()));
    assertThat(actualNote.identifier(), is(notNullValue()));
  }

  @Test
  void shouldNotCreateNoteWhenCreateNoteRequestIsMissingUsername() {
    var candidate = createCandidate();
    var noteRequest = createNoteRequest(randomString(), null);

    assertThrows(
        IllegalArgumentException.class, () -> candidateService.createNote(candidate, noteRequest));
  }

  @Test
  void shouldNotCreateNoteWhenCreateNoteRequestIsMissingText() {
    var candidate = createCandidate();
    var noteRequest = createNoteRequest(null, randomString());

    assertThrows(
        IllegalArgumentException.class, () -> candidateService.createNote(candidate, noteRequest));
  }

  @Test
  void shouldDeleteNoteWhenValidDeleteNoteRequest() {
    var candidate = setupCandidateWithNote();

    var userOrganizationId = getAnyOrganizationId(candidate);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var noteToDelete = getAnyNote(candidate);
    var deleteRequest = new DeleteNoteRequest(noteToDelete.identifier(), noteToDelete.user());
    candidateService.deleteNote(candidate, deleteRequest);

    var updatedCandidate = candidateService.getByIdentifier(candidate.identifier());
    Assertions.assertThat(updatedCandidate.getNotes()).isEmpty();
  }

  @Test
  void shouldThrowUnauthorizedOperationExceptionWhenRequesterIsNotAnOwner() {
    var candidate = setupCandidateWithNote();
    var userOrganizationId = getAnyOrganizationId(candidate);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var noteToDelete = getAnyNote(candidate);

    var deleteNoteRequest = new DeleteNoteRequest(noteToDelete.identifier(), randomString());
    assertThrows(
        UnauthorizedOperationException.class,
        () -> candidateService.deleteNote(candidate, deleteNoteRequest));
  }

  @Test
  void shouldSetUserCreatingNoteAsAssigneeIfApprovalIsUnassigned() {
    var institutionId = randomUri();
    var candidate = createCandidate(institutionId);
    var username = randomString();
    var noteRequest = new CreateNoteRequest(randomString(), username, institutionId);
    candidateService.createNote(candidate, noteRequest);
    var candidateWithNote = candidateService.getByIdentifier(candidate.identifier());

    var actualAssignee = candidateWithNote.getApprovals().get(institutionId).getAssigneeUsername();
    assertEquals(username, actualAssignee);
  }

  @Test
  void shouldNotSetUserCreatingNoteAsAssigneeIfApprovalHasAssignee() {
    var institutionId = randomUri();
    var candidate = createCandidate(institutionId);
    var existingAssignee = randomString();
    var updateAssigneeRequest = new UpdateAssigneeRequest(institutionId, existingAssignee);
    candidateService.updateApprovalAssignee(candidate, updateAssigneeRequest);

    var candidateWithAssignee = scenario.getCandidateByIdentifier(candidate.identifier());
    var noteRequest = new CreateNoteRequest(randomString(), randomString(), institutionId);
    candidateService.createNote(candidateWithAssignee, noteRequest);

    var candidateWithNote = scenario.getCandidateByIdentifier(candidate.identifier());
    var actualAssignee = candidateWithNote.getApprovals().get(institutionId).getAssigneeUsername();
    assertEquals(existingAssignee, actualAssignee);
  }

  private Candidate createCandidate(URI institutionId) {
    var request = createUpsertCandidateRequest(institutionId).build();
    return scenario.upsertCandidate(request);
  }

  private Candidate createCandidate() {
    return createCandidate(randomUri());
  }

  private NoteDto getAnyNote(Candidate candidate) {
    return candidate.getNotes().values().stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No notes found for candidate"))
        .toDto();
  }

  private Candidate setupCandidateWithNote() {
    var candidate = createCandidate();
    candidateService.createNote(candidate, randomNoteRequest());
    return candidateService.getByIdentifier(candidate.identifier());
  }
}
