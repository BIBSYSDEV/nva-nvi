package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@JsonSerialize
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonTypeName("InstitutionPoints")
public record InstitutionPoints(URI institutionId,
                                BigDecimal institutionPoints,
                                List<CreatorAffiliationPoints> creatorAffiliationPoints) {

    public static InstitutionPoints from(no.sikt.nva.nvi.common.service.model.InstitutionPoints institutionPoints) {
        return new InstitutionPoints(institutionPoints.institutionId(),
                                     institutionPoints.institutionPoints(),
                                     institutionPoints.creatorAffiliationPoints().stream()
                                         .map(CreatorAffiliationPoints::from)
                                         .toList());
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonSerialize
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
    @JsonTypeName("CreatorAffiliationPoints")
    public record CreatorAffiliationPoints(URI nviCreator, URI affiliationId, BigDecimal points) {

        public static CreatorAffiliationPoints from(
            no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints creatorAffiliationPoints) {
            return new CreatorAffiliationPoints(creatorAffiliationPoints.nviCreator(),
                                                creatorAffiliationPoints.affiliationId(),
                                                creatorAffiliationPoints.points());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private URI nviCreator;
            private URI affiliationId;

            private BigDecimal points;

            private Builder() {
            }

            public Builder withNviCreator(URI nviCreator) {
                this.nviCreator = nviCreator;
                return this;
            }

            public Builder withAffiliationId(URI affiliationId) {
                this.affiliationId = affiliationId;
                return this;
            }

            public Builder withPoints(BigDecimal points) {
                this.points = points;
                return this;
            }

            public CreatorAffiliationPoints build() {
                return new CreatorAffiliationPoints(nviCreator, affiliationId, points);
            }
        }
    }

    public static final class Builder {

        private URI institutionId;
        private BigDecimal institutionPoints;

        private List<CreatorAffiliationPoints> creatorAffiliationPoints;

        private Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withInstitutionPoints(BigDecimal institutionPoints) {
            this.institutionPoints = institutionPoints;
            return this;
        }

        public Builder withCreatorAffiliationPoints(List<CreatorAffiliationPoints> creatorAffiliationPoints) {
            this.creatorAffiliationPoints = creatorAffiliationPoints;
            return this;
        }

        public InstitutionPoints build() {
            return new InstitutionPoints(institutionId, institutionPoints, creatorAffiliationPoints);
        }
    }
}
