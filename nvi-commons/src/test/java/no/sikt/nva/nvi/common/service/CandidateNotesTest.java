package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.RequestFixtures.createNoteRequest;
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
    candidate.createNote(noteRequest);
    var userOrganizationId = getAnyOrganizationId(candidate);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var updatedCandidate = candidateService.fetch(candidate.getIdentifier());
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

    assertThrows(IllegalArgumentException.class, () -> candidate.createNote(noteRequest));
  }

  @Test
  void shouldNotCreateNoteWhenCreateNoteRequestIsMissingText() {
    var candidate = createCandidate();
    var noteRequest = createNoteRequest(null, randomString());

    assertThrows(IllegalArgumentException.class, () -> candidate.createNote(noteRequest));
  }

  @Test
  void shouldDeleteNoteWhenValidDeleteNoteRequest() {
    var candidate = createCandidate();
    var username = randomString();
    var candidateWithNote =
        candidate.createNote(new CreateNoteRequest(randomString(), username, randomUri()));
    var userOrganizationId = getAnyOrganizationId(candidateWithNote);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var noteToDelete = getAnyNote(candidate);
    candidate.deleteNote(new DeleteNoteRequest(noteToDelete.identifier(), username));

    var updatedCandidate = candidateService.fetch(candidate.getIdentifier());
    Assertions.assertThat(updatedCandidate.getNotes()).isEmpty();
  }

  @Test
  void shouldThrowUnauthorizedOperationExceptionWhenRequesterIsNotAnOwner() {
    var candidate = createCandidate();
    var candidateWithNote =
        candidate.createNote(new CreateNoteRequest(randomString(), randomString(), randomUri()));
    var userOrganizationId = getAnyOrganizationId(candidateWithNote);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var noteToDelete = getAnyNote(candidate);

    assertThrows(
        UnauthorizedOperationException.class,
        () ->
            candidate.deleteNote(new DeleteNoteRequest(noteToDelete.identifier(), randomString())));
  }

  @Test
  void shouldSetUserCreatingNoteAsAssigneeIfApprovalIsUnassigned() {
    var institutionId = randomUri();
    var candidate = createCandidate(institutionId);
    var username = randomString();
    var noteRequest = new CreateNoteRequest(randomString(), username, institutionId);
    var candidateWithNote = candidate.createNote(noteRequest);
    var actualAssignee = candidateWithNote.getApprovals().get(institutionId).getAssigneeUsername();
    assertEquals(username, actualAssignee);
  }

  @Test
  void shouldNotSetUserCreatingNoteAsAssigneeIfApprovalHasAssignee() {
    var institutionId = randomUri();
    var candidate = createCandidate(institutionId);
    var existingAssignee = randomString();
    candidate.updateApprovalAssignee(new UpdateAssigneeRequest(institutionId, existingAssignee));

    var candidateWithAssignee = scenario.getCandidateByIdentifier(candidate.getIdentifier());
    var noteRequest = new CreateNoteRequest(randomString(), randomString(), institutionId);
    candidateWithAssignee.createNote(noteRequest);

    var candidateWithNote = scenario.getCandidateByIdentifier(candidate.getIdentifier());
    var actualAssignee = candidateWithNote.getApprovals().get(institutionId).getAssigneeUsername();
    assertEquals(existingAssignee, actualAssignee);
  }

  private Candidate createCandidate(URI institutionId) {
    var request = createUpsertCandidateRequest(institutionId).build();
    Candidate.upsert(request, candidateRepository);
    return scenario.getCandidateByPublicationId(request.publicationId());
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
}
