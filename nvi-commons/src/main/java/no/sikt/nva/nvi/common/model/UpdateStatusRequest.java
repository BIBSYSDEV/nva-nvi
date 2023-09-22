package no.sikt.nva.nvi.common.model;

import no.sikt.nva.nvi.common.db.model.DbStatus;

public record UpdateStatusRequest(DbStatus approvalStatus, String username) implements UpdateApprovalRequest {

}
