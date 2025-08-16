package no.sikt.nva.nvi.common.service;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.jena.graph.Triple;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.slf4j.Logger;

public class GraphValidation {

  private final ValidationReport validationReport;

  public GraphValidation(ValidationReport validationReport) {
    this.validationReport = validationReport;
  }

  public boolean isNonConformant() {
    return !validationReport.conforms();
  }

  public boolean hasViolations() {
    return validationReport.getGraph().stream()
        .map(Triple::getObject)
        .anyMatch(SHACL.Violation::equals);
  }

  public void log(Logger logger) {
    if (!validationReport.conforms() && logger.isWarnEnabled()) {
      logger.warn(
          "Model validation failed: {}",
          generateReport().collect(Collectors.joining(System.lineSeparator())));
    }
  }

  public Stream<String> generateReport() {
    return validationReport.getEntries().stream().map(ReportEntry::message);
  }
}
