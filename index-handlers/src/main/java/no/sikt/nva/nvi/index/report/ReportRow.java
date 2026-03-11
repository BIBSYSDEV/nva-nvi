package no.sikt.nva.nvi.index.report;

import java.util.List;

@FunctionalInterface
public interface ReportRow {

  List<String> toRow();
}
