package no.sikt.nva.nvi.common.service.requests;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;

public interface UpsertCandidateRequest {

    URI publicationBucketUri();

    URI publicationId();

    boolean isApplicable();

    boolean isInternationalCollaboration();

    Map<URI, List<URI>> creators();

    String channelType();

    URI publicationChannelId();

    String level();

    String instanceType();

    PublicationDate publicationDate();

    int creatorShareCount();

    BigDecimal collaborationFactor();

    BigDecimal basePoints();

    Map<URI, BigDecimal> institutionPoints();

    BigDecimal totalPoints();
}
