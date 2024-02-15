package no.sikt.nva.nvi.events.cristin;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.unit.nva.commons.json.JsonSerializable;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record CristinNviReport(String publicationIdentifier,
                               String cristinIdentifier,
                               List<ScientificResource> scientificResources,
                               List<CristinLocale> cristinLocales,
                               String yearReported,
                               PublicationDate publicationDate,
                               String instanceType) implements JsonSerializable {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String publicationIdentifier;
        private String cristinIdentifier;
        private List<CristinLocale> cristinLocales;
        private String yearReported;
        private PublicationDate publicationDate;
        private List<ScientificResource> scientificResources;
        private String instanceType;

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

        public Builder withCristinLocales(List<CristinLocale> nviReport) {
            this.cristinLocales = nviReport;
            return this;
        }

        public Builder withScientificResources(List<ScientificResource> scientificResources) {
            this.scientificResources = scientificResources;
            return this;
        }

        public Builder withYearReported(String yearReported) {
            this.yearReported = yearReported;
            return this;
        }

        public Builder withPublicationDate(PublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public CristinNviReport build() {
            return new CristinNviReport(publicationIdentifier, cristinIdentifier, scientificResources, cristinLocales,
                                        yearReported, publicationDate, instanceType);
        }

    }
}
