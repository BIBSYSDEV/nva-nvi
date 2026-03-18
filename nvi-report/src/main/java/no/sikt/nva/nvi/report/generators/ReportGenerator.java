package no.sikt.nva.nvi.report.generators;

@FunctionalInterface
public interface ReportGenerator {

  byte[] toWorkbookByteArray();
}
