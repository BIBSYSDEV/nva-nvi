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
    }
}
