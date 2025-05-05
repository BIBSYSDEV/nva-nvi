package no.sikt.nva.nvi.common.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record PageCountDto(String firstPage, String lastPage, String numberOfPages) {}
