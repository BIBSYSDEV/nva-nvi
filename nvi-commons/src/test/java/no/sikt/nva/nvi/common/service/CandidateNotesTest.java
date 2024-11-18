package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.URI;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.exception.UnauthorizedOperationException;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateNotesTest extends LocalDynamoTest {

    public static final int YEAR = ZonedDateTime.now().getYear();
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(YEAR);
    }

    @Test
    void shouldCreateNoteWhenValidCreateNoteRequest() {
        var candidate = createCandidate();
        var noteRequest = createNoteRequest(randomString(), randomString());
        candidate.createNote(noteRequest, candidateRepository);

        var actualNote = Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository)
                             .toDto()
                             .notes()
                             .get(0);

        assertThat(noteRequest.username(), is(equalTo(actualNote.user())));
        assertThat(noteRequest.text(), is(equalTo(actualNote.text())));
        assertThat(actualNote.createdDate(), is(notNullValue()));
        assertThat(actualNote.identifier(), is(notNullValue()));
    }

    @Test
    void shouldNotCreateNoteWhenCreateNoteRequestIsMissingUsername() {
        var candidate = createCandidate();
        var noteRequest = createNoteRequest(randomString(), null);

        assertThrows(IllegalArgumentException.class, () -> candidate.createNote(noteRequest, candidateRepository));
    }

    @Test
    void shouldNotCreateNoteWhenCreateNoteRequestIsMissingText() {
        var candidate = createCandidate();
        var noteRequest = createNoteRequest(null, randomString());

        assertThrows(IllegalArgumentException.class, () -> candidate.createNote(noteRequest, candidateRepository));
    }

    @Test
    void shouldDeleteNoteWhenValidDeleteNoteRequest() {
        var candidate = createCandidate();
        var username = randomString();
        var candidateWithNote = candidate.createNote(new CreateNoteRequest(randomString(), username, randomUri()),
                                                     candidateRepository);
        var noteToDelete = candidateWithNote.toDto().notes().get(0);
        var updatedCandidate = candidate.deleteNote(new DeleteNoteRequest(noteToDelete.identifier(), username));

        assertThat(updatedCandidate.toDto().notes(), is(emptyIterable()));
    }

    @Test
    void shouldThrowUnauthorizedOperationExceptionWhenRequesterIsNotAnOwner() {
        var candidate = createCandidate();
        var candidateWithNote = candidate.createNote(
            new CreateNoteRequest(randomString(), randomString(), randomUri()), candidateRepository);
        var noteToDelete = candidateWithNote.toDto().notes().get(0);

        assertThrows(UnauthorizedOperationException.class,
                     () -> candidate.deleteNote(new DeleteNoteRequest(noteToDelete.identifier(), randomString())));
    }

    @Test
    void shouldSetUserCreatingNoteAsAssigneeIfApprovalIsUnassigned() {
        var institutionId = randomUri();
        var candidate = createCandidate(institutionId);
        var username = randomString();
        var noteRequest = new CreateNoteRequest(randomString(), username, institutionId);
        var candidateWithNote = candidate.createNote(noteRequest, candidateRepository);
        var actualAssignee = candidateWithNote.getApprovals().get(institutionId).getAssignee();
        assertEquals(username, actualAssignee.value());
    }

    @Test
    void shouldNotSetUserCreatingNoteAsAssigneeIfApprovalHasAssignee() {
        var institutionId = randomUri();
        var candidate = createCandidate(institutionId);
        var existingAssignee = randomString();
        candidate.updateApproval(new UpdateAssigneeRequest(institutionId, existingAssignee));
        var noteRequest = new CreateNoteRequest(randomString(), randomString(), institutionId);
        var candidateWithNote = candidate.createNote(noteRequest, candidateRepository);
        var actualAssignee = candidateWithNote.getApprovals().get(institutionId).getAssignee();
        assertEquals(existingAssignee, actualAssignee.value());
    }

    private Candidate createCandidate(URI institutionId) {
        var request = createUpsertCandidateRequest(institutionId);
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }

    private Candidate createCandidate() {
        return createCandidate(randomUri());
    }
}
