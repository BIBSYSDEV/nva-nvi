package no.sikt.nva.nvi.events.cristin;

import java.time.Instant;
import java.util.List;

public record CristinNviReport(String publicationIdentifier,
                               String cristinIdentifier,
                               List<CristinLocale> nviReport,
                               int yearReported,
                               Instant publicationDate) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String publicationIdentifier;
        private String cristinIdentifier;
        private List<CristinLocale> nviReport;
        private int yearReported;
        private Instant publicationDate;

        private Builder() {
        }

        public Builder withPublicationIdentifier(String publicationIdentifier) {
            this.publicationIdentifier = publicationIdentifier;
            return this;
        }

        public Builder withCristinIdentifier(String cristinIdentifier) {
            this.cristinIdentifier = cristinIdentifier;
            return this;
        }

        public Builder withNviReport(List<CristinLocale> nviReport) {
            this.nviReport = nviReport;
            return this;
        }

        public Builder withYearReported(int yearReported) {
            this.yearReported = yearReported;
            return this;
        }

        public Builder withPublicationDate(Instant publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public CristinNviReport build() {
            return new CristinNviReport(publicationIdentifier, cristinIdentifier, nviReport,
                                        yearReported, publicationDate);
        }

    }
}
