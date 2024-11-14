package no.sikt.nva.nvi.events.evaluator.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;

public record PointCalculation(InstanceType instanceType,
                               PublicationChannel channelType,
                               URI publicationChannelId,
                               Level level,
                               boolean isInternationalCollaboration,
                               BigDecimal collaborationFactor,
                               BigDecimal basePoints,
                               int creatorShareCount,
                               List<InstitutionPoints> institutionPoints,
                               BigDecimal totalPoints) {

}
