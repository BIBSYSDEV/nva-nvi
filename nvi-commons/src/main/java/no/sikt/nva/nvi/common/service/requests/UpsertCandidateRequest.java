package no.sikt.nva.nvi.common.service.requests;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface UpsertCandidateRequest {

    URI publicationBucketUri();

    URI publicationId();

    boolean isApplicable();

    boolean isInternationalCollaboration();

    Map<URI, List<URI>> creators();

    String channelType();

    URI channelId();

    String level();

    String instanceType();

    PublicationDate publicationDate();

    int creatorCount();

    int creatorShareCount();

    BigDecimal collaborationFactor();

    BigDecimal basePoints();

    Map<URI, BigDecimal> institutionPoints();

    BigDecimal totalPoints();
}
