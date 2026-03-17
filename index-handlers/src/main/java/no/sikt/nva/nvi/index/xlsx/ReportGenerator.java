package no.sikt.nva.nvi.index.xlsx;

@FunctionalInterface
public interface ReportGenerator {

  byte[] toWorkbookByteArray();
}
