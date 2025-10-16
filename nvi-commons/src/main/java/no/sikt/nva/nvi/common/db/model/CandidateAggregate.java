package no.sikt.nva.nvi.common.db.model;

import java.util.Collection;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.NoteDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wrapper class for candidate data that should be read in a single transaction. */
public record CandidateAggregate(
    CandidateDao candidate, Collection<ApprovalStatusDao> approvals, Collection<NoteDao> notes) {
  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateAggregate.class);
  private static final int ONE = 1;

  public static Optional<CandidateAggregate> fromQueryResponse(Collection<Dao> items) {
    var candidates =
        items.stream()
            .filter(CandidateDao.class::isInstance)
            .map(CandidateDao.class::cast)
            .toList();
    if (candidates.size() > ONE) {
      LOGGER.error("Expected single candidate, but found {}", candidates.size());
      throw new IllegalStateException("Multiple candidates returned by aggregate query");
    }

    var approvals =
        items.stream()
            .filter(ApprovalStatusDao.class::isInstance)
            .map(ApprovalStatusDao.class::cast)
            .toList();

    var notes = items.stream().filter(NoteDao.class::isInstance).map(NoteDao.class::cast).toList();

    return candidates.isEmpty()
        ? Optional.empty()
        : Optional.of(new CandidateAggregate(candidates.getFirst(), approvals, notes));
  }
}
