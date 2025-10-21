package no.sikt.nva.nvi.common.db.model;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.randomApplicableCandidateDao;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApprovalDao;
import static no.sikt.nva.nvi.common.db.NoteDaoFixtures.randomNoteDao;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.sikt.nva.nvi.common.db.Dao;
import org.junit.jupiter.api.Test;

class CandidateAggregateTest {

  @Test
  void shouldReturnEmptyWhenNoCandidate() {
    var items = List.<Dao>of();

    var result = CandidateAggregate.fromQueryResponse(items);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldCreateAggregateWithCandidateOnly() {
    var candidateDao = randomApplicableCandidateDao();

    var result = CandidateAggregate.fromQueryResponse(List.of(candidateDao));
    var aggregate = result.orElseThrow();

    assertThat(aggregate.candidate()).isEqualTo(candidateDao);
  }

  @Test
  void shouldCreateAggregateWithCandidateApprovalsAndNotes() {
    var candidateDao = randomApplicableCandidateDao();
    var candidateIdentifier = candidateDao.identifier();
    var approval = randomApprovalDao(candidateIdentifier, randomUri());
    var note = randomNoteDao(candidateIdentifier);
    var items = List.of(candidateDao, approval, note);

    var result = CandidateAggregate.fromQueryResponse(items);

    assertThat(result).isPresent();
    assertThat(result.get().candidate()).isEqualTo(candidateDao);
    assertThat(result.get().approvals()).containsExactly(approval);
    assertThat(result.get().notes()).containsExactly(note);
  }

  @Test
  void shouldThrowExceptionWhenMultipleCandidatesInAggregate() {
    var candidate1 = randomApplicableCandidateDao();
    var candidate2 = randomApplicableCandidateDao();

    var items = List.of(candidate1, (Dao) candidate2);
    assertThatThrownBy(() -> CandidateAggregate.fromQueryResponse(items))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Multiple candidates in input data");
  }

  @Test
  void shouldThrowExceptionWhenApprovalBelongsToDifferentCandidate() {
    var candidate = randomApplicableCandidateDao();
    var approval = randomApprovalDao(randomUUID(), randomUri());

    var items = List.of(candidate, approval);
    assertThatThrownBy(() -> CandidateAggregate.fromQueryResponse(items))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Approval does not belong to the specified candidate");
  }

  @Test
  void shouldThrowExceptionWhenNoteBelongsToDifferentCandidate() {
    var candidate = randomApplicableCandidateDao();
    var note = randomNoteDao(randomUUID());

    var items = List.of(candidate, note);
    assertThatThrownBy(() -> CandidateAggregate.fromQueryResponse(items))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Note does not belong to the specified candidate");
  }
}
