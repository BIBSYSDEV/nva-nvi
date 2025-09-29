package no.sikt.nva.nvi.common.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Context object containing all DAO objects related to a candidate, mirroring what the business
 * layer would read/write at the same time.
 */
public record CandidateDataContext(
    CandidateDao candidate,
    NviPeriodDao period,
    List<ApprovalStatusDao> approvals,
    List<NoteDao> notes) {

  public List<String> getVersions() {
    var versionList = new ArrayList<String>();
    versionList.add(candidate.version());
    versionList.add(period.version());
    for (var approval : approvals) {
      versionList.add(approval.version());
    }
    for (var note : notes) {
      versionList.add(note.version());
    }
    return versionList;
  }
}
