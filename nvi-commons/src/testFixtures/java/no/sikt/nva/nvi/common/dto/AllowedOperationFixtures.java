package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.APPROVAL_APPROVE;
import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.APPROVAL_PENDING;
import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.APPROVAL_REJECT;
import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.NOTE_CREATE;

import java.util.Set;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;

public final class AllowedOperationFixtures {
  public static final Set<CandidateOperation> CURATOR_CAN_FINALIZE_APPROVAL =
      Set.of(APPROVAL_APPROVE, APPROVAL_REJECT, NOTE_CREATE);
  public static final Set<CandidateOperation> CURATOR_CAN_RESET_APPROVAL =
      Set.of(APPROVAL_REJECT, APPROVAL_PENDING, NOTE_CREATE);
  public static final Set<CandidateOperation> CURATOR_CANNOT_UPDATE_APPROVAL = Set.of(NOTE_CREATE);

  private AllowedOperationFixtures() {
    // Utility class
  }
}
