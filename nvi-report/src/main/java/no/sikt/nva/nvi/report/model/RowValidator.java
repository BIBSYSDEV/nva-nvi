package no.sikt.nva.nvi.report.model;

@FunctionalInterface
public interface RowValidator {

  void validate(Row row);
}
