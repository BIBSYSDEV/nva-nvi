package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UndisputedCandidatesByLocalApprovalStatus(
    @JsonProperty("new") int newCount,
    @JsonProperty("pending") int pendingCount,
    @JsonProperty("approved") int approvedCount,
    @JsonProperty("rejected") int rejectedCount) {}
