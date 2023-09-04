package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Candidate(URI publicationId,
                        URI publicationBucketUri,
                        boolean isApplicable,
                        String instanceType,
                        Level level,
                        PublicationDate publicationDate,
                        boolean isInternationalCollaboration,
                        int creatorCount,
                        List<Creator> creators,
                        Map<URI, BigDecimal> points,
                        List<ApprovalStatus> approvalStatuses,
                        List<Note> notes) {

    public static final String PUBLICATION_ID_FIELD = "publicationId";
    public static final String PUBLICATION_BUCKET_URI_FIELD = "publicationBucketUri";
    public static final String IS_APPLICABLE_FIELD = "isApplicable";
    public static final String INSTANCE_TYPE_FIELD = "instanceType";
    public static final String LEVEL_FIELD = "level";
    public static final String PUBLICATION_DATE_FIELD = "publicationDate";
    public static final String IS_INTERNATIONAL_COLLABORATION_FIELD = "isInternationalCollaboration";
    public static final String CREATOR_COUNT_FIELD = "creatorCount";
    public static final String CREATORS_FIELD = "creators";
    public static final String POINTS_FIELD = "points";
    public static final String APPROVAL_STATUSES_FIELD = "approvalStatuses";
    public static final String NOTES_FIELD = "notes";

    public Candidate {
    }

    public static Candidate fromDynamoDb(AttributeValue input) {
        var map = input.m();
        return new Builder()
                   .withPublicationId(URI.create(map.get(PUBLICATION_ID_FIELD).s()))
                   .withPublicationBucketUri(Optional.ofNullable(map.get(PUBLICATION_BUCKET_URI_FIELD))
                                                 .map(AttributeValue::s).map(URI::create).orElse(null))
                   .withIsApplicable(map.get(IS_APPLICABLE_FIELD).bool())
                   .withInstanceType(map.get(INSTANCE_TYPE_FIELD).s())
                   .withLevel(Level.parse(map.get(LEVEL_FIELD).s()))
                   .withPublicationDate(PublicationDate.fromDynamoDb(map.get(PUBLICATION_DATE_FIELD)))
                   .withIsInternationalCollaboration(map.get(IS_INTERNATIONAL_COLLABORATION_FIELD).bool())
                   .withCreatorCount(Integer.parseInt(map.get(CREATOR_COUNT_FIELD).n()))
                   .withCreators(
                       map.get(CREATORS_FIELD).l().stream().map(Creator::fromDynamoDb).toList()
                   ).withPoints(
                map.get(POINTS_FIELD).m().entrySet().stream()
                    .collect(Collectors.toMap(entry -> URI.create(entry.getKey()),
                                              entry -> new BigDecimal(entry.getValue().n()))))
                   .withApprovalStatuses(
                       map.get(APPROVAL_STATUSES_FIELD).l()
                           .stream().map(ApprovalStatus::fromDynamoDb).toList()
                   )
                   .withNotes(Optional.ofNullable(map.get(NOTES_FIELD))
                                  .map(AttributeValue::l).map(l -> l.stream().map(Note::fromDynamoDb)
                                                                       .toList()).orElse(null)
                   )
                   .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder()
                   .withPublicationId(publicationId)
                   .withPublicationBucketUri(publicationBucketUri)
                   .withIsApplicable(isApplicable)
                   .withInstanceType(instanceType)
                   .withLevel(level)
                   .withPublicationDate(publicationDate)
                   .withIsInternationalCollaboration(isInternationalCollaboration)
                   .withCreatorCount(creatorCount)
                   .withCreators(creators)
                   .withPoints(points)
                   .withApprovalStatuses(approvalStatuses)
                   .withNotes(notes);
    }

    public AttributeValue toDynamoDb() {
        var map = new HashMap<String, AttributeValue>();
        map.put(PUBLICATION_ID_FIELD, AttributeValue.fromS(publicationId.toString()));
        if (publicationBucketUri != null) {
            map.put(PUBLICATION_BUCKET_URI_FIELD, AttributeValue.fromS(publicationBucketUri.toString()));
        }
        map.put(IS_APPLICABLE_FIELD, AttributeValue.fromBool(isApplicable));
        map.put(INSTANCE_TYPE_FIELD, AttributeValue.fromS(instanceType));
        map.put(LEVEL_FIELD, AttributeValue.fromS(level().getValue()));
        map.put(PUBLICATION_DATE_FIELD, publicationDate.toDynamoDb());
        map.put(IS_INTERNATIONAL_COLLABORATION_FIELD, AttributeValue.fromBool(isInternationalCollaboration));
        map.put(CREATOR_COUNT_FIELD, AttributeValue.fromN(String.valueOf(creatorCount)));
        map.put(CREATORS_FIELD, AttributeValue.fromL(creators.stream().map(Creator::toDynamoDb).toList()));
        map.put(APPROVAL_STATUSES_FIELD,
                AttributeValue.fromL(approvalStatuses.stream().map(ApprovalStatus::toDynamoDb).toList())
        );
        if (notes != null) {
            map.put(NOTES_FIELD, AttributeValue.fromL(notes.stream().map(Note::toDynamoDb).toList()));
        }
        map.put(POINTS_FIELD,
                AttributeValue.fromM(
                    points.entrySet().stream().collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()),
                                                                        entry -> AttributeValue.fromN(
                                                                            entry.getValue().toString())))));

        return AttributeValue.fromM(map);
    }

    public static final class Builder {

        private URI publicationId;
        private URI publicationBucketUri;
        private boolean isApplicable;
        private String instanceType;
        private Level level;
        private PublicationDate publicationDate;
        private boolean isInternationalCollaboration;
        private int creatorCount;
        private List<Creator> creators;
        private Map<URI, BigDecimal> points;
        private List<ApprovalStatus> approvalStatuses;
        private List<Note> notes;

        private Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder withIsApplicable(boolean isApplicable) {
            this.isApplicable = isApplicable;
            return this;
        }

        public Builder withInstanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder withLevel(Level level) {
            this.level = level;
            return this;
        }

        public Builder withPublicationDate(PublicationDate publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
            this.isInternationalCollaboration = isInternationalCollaboration;
            return this;
        }

        public Builder withCreatorCount(int creatorCount) {
            this.creatorCount = creatorCount;
            return this;
        }

        public Builder withCreators(List<Creator> creators) {
            this.creators = creators;
            return this;
        }

        public Builder withPoints(Map<URI, BigDecimal> points) {
            this.points = points;
            return this;
        }

        public Builder withApprovalStatuses(List<ApprovalStatus> approvalStatuses) {
            this.approvalStatuses = approvalStatuses;
            return this;
        }

        public Builder withNotes(List<Note> notes) {
            this.notes = notes;
            return this;
        }

        public Candidate build() {
            return new Candidate(publicationId, publicationBucketUri, isApplicable, instanceType, level,
                                 publicationDate, isInternationalCollaboration, creatorCount, creators, points,
                                 approvalStatuses, notes);
        }
    }
}
