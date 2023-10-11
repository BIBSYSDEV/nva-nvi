package no.sikt.nva.nvi.evaluator.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

public record PointCalculation(InstanceType instanceType,
                               PublicationChannel channelType,
                               URI publicationChannelId,
                               Level level,
                               boolean isInternationalCollaboration,
                               BigDecimal collaborationFactor,
                               BigDecimal basePoints,
                               int creatorShareCount,
                               Map<URI, BigDecimal> institutionPoints) {

}
