# nvi-report

Generating NVI reports in CSV and XLSX formats.

## Core concepts

A **`Row`** is a list of typed **`Cell`s**. Each cell has a **`Header`** (a column identifier) and
a value. Cells are sorted by
their header's `ordinal()` when the row is built, so the enum declaration order is the column order.

A **`RowBuilder`** provides a base class for constructing rows and enforces validity via
`validate()`. A **`RowValidator`** checks the cells — e.g. `DefaultValidator` requires all headers
to be present exactly once.

**Generators** (`CsvGenerator`, `XlsxGenerator`) accept any `List<Row>` and are
report-agnostic. The first row in the output always contains the column headers, derived from the
first row passed to the generator. The returned byte array should be written with a `.csv` or
`.xlsx` extension respectively to produce a valid file.

## Adding a new report type

1. Create an enum implementing `Header` for your columns.
2. Create a builder extending `RowBuilder` with builder for new report type.
3. Pass the built **`List<Row>`** to a generator to produce a report.
