package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UndisputedCandidatesByLocalApprovalStatus(
    @JsonProperty("new") int newCount,
    @JsonProperty("pending") int pendingCount,
    @JsonProperty("approved") int approvedCount,
    @JsonProperty("rejected") int rejectedCount) {}
