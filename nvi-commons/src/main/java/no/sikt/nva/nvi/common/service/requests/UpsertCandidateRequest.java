package no.sikt.nva.nvi.common.service.requests;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.model.UnverifiedNviCreator;

public interface UpsertCandidateRequest {

    URI publicationBucketUri();

    URI publicationId();

    boolean isApplicable();

    boolean isInternationalCollaboration();

    Map<URI, List<URI>> creators();

    List<UnverifiedNviCreator> unverifiedCreators();

    String channelType();

    URI publicationChannelId();

    String level();

    InstanceType instanceType();

    PublicationDate publicationDate();

    int creatorShareCount();

    BigDecimal collaborationFactor();

    BigDecimal basePoints();

    List<InstitutionPoints> institutionPoints();

    BigDecimal totalPoints();
}
