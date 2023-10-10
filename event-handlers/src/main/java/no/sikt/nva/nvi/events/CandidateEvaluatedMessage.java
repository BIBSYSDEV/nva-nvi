package no.sikt.nva.nvi.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record CandidateEvaluatedMessage(
    URI publicationBucketUri,
    CandidateType candidate
) implements UpsertCandidateRequest {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public URI publicationId() {
        return candidate().publicationId();
    }

    @Override
    public boolean isApplicable() {
        return false;
    }

    @Override
    public boolean isInternationalCooperation() {
        return false;
    }

    @Override
    public Map<URI, List<URI>> creators() {
        return null;
    }

    @Override
    public String level() {
        return null;
    }

    @Override
    public String instanceType() {
        return null;
    }

    @Override
    public PublicationDate publicationDate() {
        return null;
    }

    @Override
    public Map<URI, BigDecimal> points() {
        return null;
    }

    @Override
    public int creatorCount() {
        return 0;
    }

    public static final class Builder {

        private URI publicationBucketUri;
        private CandidateType candidate;

        private Builder() {
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder withCandidateType(CandidateType candidate) {
            this.candidate = candidate;
            return this;
        }

        public CandidateEvaluatedMessage build() {
            return new CandidateEvaluatedMessage(publicationBucketUri, candidate);
        }
    }
}