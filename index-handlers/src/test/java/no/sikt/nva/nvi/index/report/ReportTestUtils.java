package no.sikt.nva.nvi.index.report;

import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.List;
import java.util.function.Predicate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;

public final class ReportTestUtils {

  ReportTestUtils() {}

  public static Predicate<NviCandidateIndexDocument> hasGlobalStatus(GlobalApprovalStatus status) {
    return document -> document.globalApprovalStatus() == status;
  }

  public static Predicate<NviCandidateIndexDocument> hasLocalStatus(
      URI institutionId, ApprovalStatus status) {
    return document -> document.getApprovalStatusForInstitution(institutionId) == status;
  }

  public static Predicate<NviCandidateIndexDocument> hasApprovalFor(URI institutionId) {
    return document -> nonNull(document.getApprovalForInstitution(institutionId));
  }

  public static List<NviCandidateIndexDocument> getRelevantDocuments(
      List<NviCandidateIndexDocument> documents, URI institutionId) {
    return documents.stream().filter(hasApprovalFor(institutionId)).toList();
  }
}
