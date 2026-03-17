package no.sikt.nva.nvi.index.report.model;

import java.util.List;

public sealed interface RowBuilder permits ReportRowBuilder {

  List<Cell> cells();

  Row build();
}
