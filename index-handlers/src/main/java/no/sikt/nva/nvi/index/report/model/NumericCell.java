package no.sikt.nva.nvi.index.report.model;

import java.math.BigDecimal;

record NumericCell(Header header, BigDecimal value) implements Cell {}
