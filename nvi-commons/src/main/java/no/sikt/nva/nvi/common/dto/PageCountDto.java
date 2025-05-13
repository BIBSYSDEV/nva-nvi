package no.sikt.nva.nvi.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record PageCountDto(String first, String last, String total) {}
