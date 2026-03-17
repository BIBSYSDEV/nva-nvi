package no.sikt.nva.nvi.index.report.model;

@FunctionalInterface
public interface RowValidator {

  void validate(Row row);
}
