package no.sikt.nva.nvi.common.db.model;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.service.NviPeriodService.findByPublishingYear;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.Dao;
import no.sikt.nva.nvi.common.db.NoteDao;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import nva.commons.core.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wrapper class for candidate data that should be read in a single transaction. */
public record CandidateAggregate(
    CandidateDao candidate, Collection<ApprovalStatusDao> approvals, Collection<NoteDao> notes) {
  private static final Logger LOGGER = LoggerFactory.getLogger(CandidateAggregate.class);
  private static final int ONE = 1;

  public static Optional<CandidateAggregate> fromQueryResponse(Collection<Dao> items) {
    var optionalCandidate = findCandidate(items);
    if (optionalCandidate.isEmpty()) {
      return Optional.empty();
    }
    var candidate = optionalCandidate.get();
    var approvals = findApprovals(candidate.identifier(), items);
    var notes = findNotes(candidate.identifier(), items);

    return Optional.of(new CandidateAggregate(candidate, approvals, notes));
  }

  public Candidate toCandidate(Environment environment, Collection<NviPeriod> allPeriods) {
    var publicationYear = candidate.getPeriodYear();
    var period = findByPublishingYear(allPeriods, publicationYear).orElse(null);
    return Candidate.fromDao(candidate, approvals, notes, period, environment);
  }

  private static Optional<CandidateDao> findCandidate(Collection<Dao> items) {
    var candidates =
        items.stream()
            .filter(CandidateDao.class::isInstance)
            .map(CandidateDao.class::cast)
            .toList();
    if (candidates.size() > ONE) {
      LOGGER.error("Expected single candidate, but found {}", candidates.size());
      throw new IllegalArgumentException("Multiple candidates in input data");
    }
    return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
  }

  private static Collection<ApprovalStatusDao> findApprovals(
      UUID candidateIdentifier, Collection<Dao> items) {
    var approvals =
        items.stream()
            .filter(ApprovalStatusDao.class::isInstance)
            .map(ApprovalStatusDao.class::cast)
            .toList();

    var mismatchedApproval =
        approvals.stream()
            .map(ApprovalStatusDao::identifier)
            .filter(not(candidateIdentifier::equals))
            .findFirst();

    if (mismatchedApproval.isPresent()) {
      LOGGER.error(
          "Found approval with identifier {} that does not match candidate {}",
          mismatchedApproval.get(),
          candidateIdentifier);
      throw new IllegalArgumentException("Approval does not belong to the specified candidate");
    }

    return approvals;
  }

  private static Collection<NoteDao> findNotes(UUID candidateIdentifier, Collection<Dao> items) {
    var notes = items.stream().filter(NoteDao.class::isInstance).map(NoteDao.class::cast).toList();

    var mismatchedNote =
        notes.stream()
            .map(NoteDao::identifier)
            .filter(not(candidateIdentifier::equals))
            .findFirst();

    if (mismatchedNote.isPresent()) {
      LOGGER.error(
          "Found note with identifier {} that does not match candidate {}",
          mismatchedNote.get(),
          candidateIdentifier);
      throw new IllegalArgumentException("Note does not belong to the specified candidate");
    }

    return notes;
  }
}
