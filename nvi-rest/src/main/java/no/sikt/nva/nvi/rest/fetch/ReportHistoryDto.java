package no.sikt.nva.nvi.rest.fetch;

import java.net.URI;

public record ReportHistoryDto(URI publicationId, ReportStatusDto reportingStatus, String year) {

    public enum ReportStatusDto {

        REPORTED("Reported"),
        NOT_REPORTED("Not reported");
        private final String value;

        ReportStatusDto(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
