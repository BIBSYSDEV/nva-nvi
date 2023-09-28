package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CandidateBONotesTest extends LocalDynamoTest {

    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
    }

    @Test
    void shouldCreateNoteWhenValidCreateNoteRequest() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var noteRequest = createNoteRequest(randomString(), randomString());
        var actualNote = candidate.createNote(noteRequest).toDto().notes().get(0);

        assertThat(noteRequest.username(), is(equalTo(actualNote.user())));
        assertThat(noteRequest.text(), is(equalTo(actualNote.text())));
    }

    @Test
    void shouldNotCreateNoteWhenCreateNoteRequestIsMissingUsername() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var noteRequest = createNoteRequest(randomString(), null);

        assertThrows(IllegalArgumentException.class, () -> candidate.createNote(noteRequest));
    }

    @Test
    void shouldNotCreateNoteWhenCreateNoteRequestIsMissingText() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        var noteRequest = createNoteRequest(null, randomString());

        assertThrows(IllegalArgumentException.class, () -> candidate.createNote(noteRequest));
    }

    @Test
    void shouldDeleteNoteWhenValidDeleteNoteRequest() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository);
        TestUtils.createNotes(candidate, 3);
        var deletedNoteIdentifier = candidate.toDto().notes().get(0).identifier();
        var latestCandidate = candidate.deleteNote(() -> deletedNoteIdentifier);
        var anyNotesWithDeletedIdentifier = latestCandidate.toDto()
                                                .notes()
                                                .stream()
                                                .anyMatch(note -> note.identifier() == deletedNoteIdentifier);
        assertThat(latestCandidate.toDto().notes().size(), is(2));
        assertThat(anyNotesWithDeletedIdentifier, is(false));
    }
}
