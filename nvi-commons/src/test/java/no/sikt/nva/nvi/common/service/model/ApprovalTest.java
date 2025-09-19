package no.sikt.nva.nvi.common.service.model;

import static java.util.UUID.randomUUID;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApprovalTest {

  @Test
  void shouldReturnTrueWhenApprovalIsPendingAndUnassigned() {
    var identifier = UUID.randomUUID();
    var approval =
        new Approval(
            Mockito.mock(CandidateRepository.class), identifier, createPendingApproval(identifier));
    assertTrue(approval.isPendingAndUnassigned());
  }

  private static ApprovalStatusDao createPendingApproval(UUID identifier) {
    return ApprovalStatusDao.builder()
        .identifier(identifier)
        .approvalStatus(createPendingApproval())
        .version(randomUUID().toString())
        .build();
  }

  private static DbApprovalStatus createPendingApproval() {
    return new DbApprovalStatus(randomUri(), DbStatus.PENDING, null, null, null, null);
  }
}
