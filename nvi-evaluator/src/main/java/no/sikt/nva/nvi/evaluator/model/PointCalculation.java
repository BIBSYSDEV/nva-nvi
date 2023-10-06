package no.sikt.nva.nvi.evaluator.model;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

public record PointCalculation(PublicationChannel channelType,
                               URI publicationChannelId,
                               boolean isInternationalCollaboration,
                               BigDecimal collaborationFactor,
                               BigDecimal basePoints,
                               Map<URI, BigDecimal> institutionPoints) {

}
