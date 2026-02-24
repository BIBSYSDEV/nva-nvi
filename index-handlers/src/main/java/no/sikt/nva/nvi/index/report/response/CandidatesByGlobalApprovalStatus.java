package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record CandidatesByGlobalApprovalStatus(
    int dispute, int pending, int rejected, int approved) {}
