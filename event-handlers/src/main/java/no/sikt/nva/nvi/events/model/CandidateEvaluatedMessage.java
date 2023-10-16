package no.sikt.nva.nvi.events.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.events.model.NviCandidate.Creator;

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
        return isNviCandidate();
    }

    @Override
    public boolean isInternationalCollaboration() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.isInternationalCollaboration();
        }
        return false;
    }

    @Override
    public Map<URI, List<URI>> creators() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.verifiedCreators()
                       .stream()
                       .collect(Collectors.toMap(Creator::id, Creator::nviInstitutions));
        }
        return Collections.emptyMap();
    }

    @Override
    public String channelType() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.channelType();
        }
        return null;
    }

    @Override
    public URI channelId() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.publicationChannelId();
        }
        return null;
    }

    @Override
    public String level() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.level();
        }
        return null;
    }

    @Override
    public String instanceType() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.instanceType();
        }
        return null;
    }

    @Override
    public PublicationDate publicationDate() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return mapToPublicationDate(nviCandidate.publicationDate());
        }
        return null;
    }

    @Override
    @Deprecated
    public int creatorCount() {
        return 0;
    }

    @Override
    public int creatorShareCount() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.creatorShareCount();
        }
        return 0;
    }

    @Override
    public BigDecimal collaborationFactor() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.collaborationFactor();
        }
        return null;
    }

    @Override
    public BigDecimal basePoints() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.basePoints();
        }
        return null;
    }

    @Override
    public Map<URI, BigDecimal> institutionPoints() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.institutionPoints();
        }
        return Collections.emptyMap();
    }

    @Override
    public BigDecimal totalPoints() {
        if (isNviCandidate()) {
            var nviCandidate = (NviCandidate) candidate;
            return nviCandidate.totalPoints();
        }
        return null;
    }

    private static PublicationDate mapToPublicationDate(NviCandidate.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private boolean isNviCandidate() {
        return candidate instanceof NviCandidate;
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