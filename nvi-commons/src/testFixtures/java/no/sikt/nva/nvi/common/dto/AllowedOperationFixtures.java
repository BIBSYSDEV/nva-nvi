package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.APPROVAL_APPROVE;
import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.APPROVAL_PENDING;
import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.APPROVAL_REJECT;
import static no.sikt.nva.nvi.common.service.dto.CandidateOperation.NOTE_CREATE;

import java.util.List;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;

public final class AllowedOperationFixtures {
  public static final List<CandidateOperation> CAN_FINALIZE_APPROVAL =
      List.of(APPROVAL_APPROVE, APPROVAL_REJECT, NOTE_CREATE);
  public static final List<CandidateOperation> CAN_RESET_APPROVAL = List.of(APPROVAL_REJECT, APPROVAL_PENDING,
                                                                            NOTE_CREATE);
  public static final List<CandidateOperation> CANNOT_UPDATE_APPROVAL = List.of(NOTE_CREATE);

  private AllowedOperationFixtures() {
    // Utility class
  }
}
