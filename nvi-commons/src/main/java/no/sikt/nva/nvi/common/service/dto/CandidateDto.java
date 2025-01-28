package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeName(CandidateDto.NVI_CANDIDATE)
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CandidateDto(
    URI id,
    URI context,
    UUID identifier,
    URI publicationId,
    List<ApprovalDto> approvals,
    BigDecimal totalPoints,
    List<NoteDto> notes,
    PeriodStatusDto period,
    String status,
    Collection<CandidateOperation> allowedOperations)
    implements JsonSerializable {

  public static final String NVI_CANDIDATE = "NviCandidate";

  public static Builder builder() {
    return new Builder();
  }

  public Builder copy() {
    return new Builder()
        .withId(id)
        .withContext(context)
        .withIdentifier(identifier)
        .withPublicationId(publicationId)
        .withApprovals(approvals)
        .withTotalPoints(totalPoints)
        .withNotes(notes)
        .withPeriod(period)
        .withReportStatus(status)
        .withAllowedOperations(allowedOperations);
  }

  public static final class Builder {

    private URI id;
    private URI context;
    private UUID identifier;
    private URI publicationId;
    private List<ApprovalDto> approvals;
    private BigDecimal totalPoints;
    private List<NoteDto> notes;
    private PeriodStatusDto periodStatus;
    private String reportStatus;
    private Collection<CandidateOperation> allowedOperations;

    private Builder() {}

    public Builder withId(URI id) {
      this.id = id;
      return this;
    }

    public Builder withContext(URI context) {
      this.context = context;
      return this;
    }

    public Builder withIdentifier(UUID identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder withPublicationId(URI publicationId) {
      this.publicationId = publicationId;
      return this;
    }

    public Builder withApprovals(List<ApprovalDto> approvals) {
      this.approvals = approvals;
      return this;
    }

    public Builder withTotalPoints(BigDecimal totalPoints) {
      this.totalPoints = totalPoints;
      return this;
    }

    public Builder withNotes(List<NoteDto> notes) {
      this.notes = notes;
      return this;
    }

    public Builder withPeriod(PeriodStatusDto periodStatus) {
      this.periodStatus = periodStatus;
      return this;
    }

    public Builder withReportStatus(String reportStatus) {
      this.reportStatus = reportStatus;
      return this;
    }

    public Builder withAllowedOperations(Collection<CandidateOperation> allowedOperations) {
      this.allowedOperations = allowedOperations;
      return this;
    }

    public CandidateDto build() {
      return new CandidateDto(
          id,
          context,
          identifier,
          publicationId,
          approvals,
          totalPoints,
          notes,
          periodStatus,
          reportStatus,
          allowedOperations);
    }
  }
}
