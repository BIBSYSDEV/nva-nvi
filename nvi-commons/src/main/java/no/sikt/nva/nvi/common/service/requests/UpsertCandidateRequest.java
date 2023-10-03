package no.sikt.nva.nvi.common.service.requests;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface UpsertCandidateRequest {

    URI publicationBucketUri();

    URI publicationId();

    boolean isApplicable();

    boolean isInternationalCooperation();

    Map<URI, List<URI>> creators();

    String level();

    String instanceType();

    PublicationDate publicationDate();

    Map<URI, BigDecimal> points();

    int creatorCount();
}
