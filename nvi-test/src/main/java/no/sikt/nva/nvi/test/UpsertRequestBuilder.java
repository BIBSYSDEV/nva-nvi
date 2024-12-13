package no.sikt.nva.nvi.test;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;

public class UpsertRequestBuilder {

    public static final URI creatorId = randomUri();
    public static final URI affiliationId = randomUri();
    private URI publicationBucketUri = URI.create("http://example.org/publicationBucketUri");
    private URI publicationId = URI.create("http://example.org/publicationId");
    private boolean isApplicable = true;
    private boolean isInternationalCollaboration = true;
    private Map<URI, List<URI>> creators = Map.of(creatorId, List.of(affiliationId));
    private String channelType = "channelType";
    private URI channelId = URI.create("http://example.org/channelId");
    private String level = "LevelOne";
    private InstanceType instanceType = InstanceType.ACADEMIC_ARTICLE;
    private PublicationDate publicationDate = new PublicationDate("2023", "01", "01");
    private int creatorShareCount = 1;
    private BigDecimal collaborationFactor = BigDecimal.ONE;
    private BigDecimal basePoints = BigDecimal.ONE;
    private List<InstitutionPoints> points = List.of(new InstitutionPoints(randomUri(), randomBigDecimal(),
                                                                           List.of(new CreatorAffiliationPoints(
                                                                               creatorId, affiliationId,
                                                                               randomBigDecimal()))));
    private BigDecimal totalPoints = BigDecimal.ONE;

    public static UpsertRequestBuilder fromRequest(UpsertCandidateRequest request) {
        return new UpsertRequestBuilder()
                   .withPublicationBucketUri(request.publicationBucketUri())
                   .withPublicationId(request.publicationId())
                   .withIsApplicable(request.isApplicable())
                   .withIsInternationalCollaboration(request.isInternationalCollaboration())
                   .withCreators(request.creators())
                   .withChannelType(request.channelType())
                   .withChannelId(request.publicationChannelId())
                   .withLevel(request.level())
                   .withInstanceType(request.instanceType())
                   .withPublicationDate(request.publicationDate())
                   .withCreatorShareCount(request.creatorShareCount())
                   .withCollaborationFactor(request.collaborationFactor())
                   .withBasePoints(request.basePoints())
                   .withPoints(request.institutionPoints())
                   .withTotalPoints(request.totalPoints());
    }

    public UpsertRequestBuilder withPublicationBucketUri(URI publicationBucketUri) {
        this.publicationBucketUri = publicationBucketUri;
        return this;
    }

    public UpsertRequestBuilder withPublicationId(URI publicationId) {
        this.publicationId = publicationId;
        return this;
    }

    public UpsertRequestBuilder withIsApplicable(boolean isApplicable) {
        this.isApplicable = isApplicable;
        return this;
    }

    public UpsertRequestBuilder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
        this.isInternationalCollaboration = isInternationalCollaboration;
        return this;
    }

    public UpsertRequestBuilder withCreators(Map<URI, List<URI>> creators) {
        this.creators = creators;
        return this;
    }

    public UpsertRequestBuilder withChannelType(String channelType) {
        this.channelType = channelType;
        return this;
    }

    public UpsertRequestBuilder withChannelId(URI channelId) {
        this.channelId = channelId;
        return this;
    }

    public UpsertRequestBuilder withLevel(String level) {
        this.level = level;
        return this;
    }

    public UpsertRequestBuilder withInstanceType(InstanceType instanceType) {
        this.instanceType = instanceType;
        return this;
    }

    public UpsertRequestBuilder withPublicationDate(PublicationDate publicationDate) {
        this.publicationDate = publicationDate;
        return this;
    }

    public UpsertRequestBuilder withCreatorShareCount(int creatorShareCount) {
        this.creatorShareCount = creatorShareCount;
        return this;
    }

    public UpsertRequestBuilder withCollaborationFactor(BigDecimal collaborationFactor) {
        this.collaborationFactor = collaborationFactor;
        return this;
    }

    public UpsertRequestBuilder withBasePoints(BigDecimal basePoints) {
        this.basePoints = basePoints;
        return this;
    }

    public UpsertRequestBuilder withPoints(List<InstitutionPoints> points) {
        this.points = points;
        return this;
    }

    public UpsertRequestBuilder withTotalPoints(BigDecimal totalPoints) {
        this.totalPoints = totalPoints;
        return this;
    }

    public UpsertCandidateRequest build() {

        return new UpsertCandidateRequest() {

            @Override
            public URI publicationBucketUri() {
                return publicationBucketUri;
            }

            @Override
            public URI publicationId() {
                return publicationId;
            }

            @Override
            public boolean isApplicable() {
                return isApplicable;
            }

            @Override
            public boolean isInternationalCollaboration() {
                return isInternationalCollaboration;
            }

            @Override
            public Map<URI, List<URI>> creators() {
                return creators;
            }

            @Override
            public String channelType() {
                return channelType;
            }

            @Override
            public URI publicationChannelId() {
                return channelId;
            }

            @Override
            public String level() {
                return level;
            }

            @Override
            public InstanceType instanceType() {
                return instanceType;
            }

            @Override
            public PublicationDate publicationDate() {
                return publicationDate;
            }

            @Override
            public int creatorShareCount() {
                return creatorShareCount;
            }

            @Override
            public BigDecimal collaborationFactor() {
                return collaborationFactor;
            }

            @Override
            public BigDecimal basePoints() {
                return basePoints;
            }

            @Override
            public List<InstitutionPoints> institutionPoints() {
                return points;
            }

            @Override
            public BigDecimal totalPoints() {
                return totalPoints;
            }
        };
    }
}
