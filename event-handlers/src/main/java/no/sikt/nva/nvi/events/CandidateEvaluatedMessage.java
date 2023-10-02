package no.sikt.nva.nvi.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.CandidateDetails.Creator;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record CandidateEvaluatedMessage(
    CandidateStatus status,
    URI publicationBucketUri,
    CandidateDetails candidateDetails,
    Map<URI, BigDecimal> institutionPoints
) implements UpsertCandidateRequest {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public URI publicationId() {
        return candidateDetails.publicationId();
    }

    @Override
    public boolean isApplicable() {
        return CandidateStatus.CANDIDATE.equals(status);
    }

    @Override
    public boolean isInternationalCooperation() {
        return false;
    }

    @Override
    public Map<URI, List<URI>> creators() {
        return candidateDetails.verifiedCreators() != null
                   ? candidateDetails.verifiedCreators().stream()
                         .collect(Collectors.toMap(Creator::id, Creator::nviInstitutions))
                   : Collections.emptyMap();
    }

    @Override
    public String level() {
        return candidateDetails.level();
    }

    @Override
    public String instanceType() {
        return candidateDetails.instanceType();
    }

    @Override
    public PublicationDate publicationDate() {
        return mapToPublicationDate(candidateDetails.publicationDate());
    }

    @Override
    public Map<URI, BigDecimal> points() {
        return institutionPoints != null ? institutionPoints : Collections.emptyMap();
    }

    @Override
    public int creatorCount() {
        return 0;
    }

    private PublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return publicationDate != null
                   ? new PublicationDate(publicationDate.year(), publicationDate.month(),
                                         publicationDate.day())
                   : null;
    }

    public static final class Builder {

        private CandidateStatus status;
        private URI publicationBucketUri;
        private CandidateDetails candidateDetails;
        private Map<URI, BigDecimal> institutionPoints;

        private Builder() {
        }

        public Builder withStatus(CandidateStatus status) {
            this.status = status;
            return this;
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder withCandidateDetails(CandidateDetails candidateDetails) {
            this.candidateDetails = candidateDetails;
            return this;
        }

        public Builder withInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
            this.institutionPoints = institutionPoints;
            return this;
        }

        public CandidateEvaluatedMessage build() {
            return new CandidateEvaluatedMessage(status, publicationBucketUri, candidateDetails, institutionPoints);
        }
    }
}