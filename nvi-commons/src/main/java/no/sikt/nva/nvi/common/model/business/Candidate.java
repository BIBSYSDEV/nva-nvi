package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nva.commons.core.JacocoGenerated;
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
                        List<ApprovalStatus> approvalStatuses,
                        List<Note> notes) {

    public Candidate {
    }

    public AttributeValue toDynamoDb() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("publicationId", AttributeValue.fromS(publicationId.toString()));
        if (publicationBucketUri != null) map.put("publicationBucketUri", AttributeValue.fromS(publicationBucketUri.toString()));
        map.put("isApplicable", AttributeValue.fromBool(isApplicable));
        map.put("instanceType", AttributeValue.fromS(instanceType));
        map.put("level", AttributeValue.fromS(level().getValue()));
        map.put("publicationDate", publicationDate.toDynamoDb());
        map.put("isInternationalCollaboration", AttributeValue.fromBool(isInternationalCollaboration));
        map.put("creatorCount", AttributeValue.fromN(String.valueOf(creatorCount)));
        map.put("creators", AttributeValue.fromL(creators.stream().map(Creator::toDynamoDb).toList()));
        map.put("approvalStatuses", AttributeValue.fromL(approvalStatuses.stream().map(ApprovalStatus::toDynamoDb).toList()));
        if (notes != null) map.put("notes", AttributeValue.fromL(notes.stream().map(Note::toDynamoDb).toList()));
        return AttributeValue.fromM(map);
    }

    public static Candidate fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Builder()
            .withPublicationId(URI.create(map.get("publicationId").s()))
            .withPublicationBucketUri(Optional.ofNullable(map.get("publicationBucketUri")).map(AttributeValue::s).map(URI::create).orElse(null))
            .withIsApplicable(map.get("isApplicable").bool())
            .withInstanceType(map.get("instanceType").s())
            .withLevel(Level.parse(map.get("level").s()))
            .withPublicationDate(PublicationDate.fromDynamoDb(map.get("publicationDate")))
            .withIsInternationalCollaboration(map.get("isInternationalCollaboration").bool())
            .withCreatorCount(Integer.parseInt(map.get("creatorCount").n()))
            .withCreators(map.get("creators").l().stream().map(Creator::fromDynamoDb).collect(Collectors.toList()))
            .withApprovalStatuses(map.get("approvalStatuses").l().stream().map(ApprovalStatus::fromDynamoDb).collect(Collectors.toList()))
            .withNotes(Optional.ofNullable(map.get("notes")).map(AttributeValue::l).map(l -> l.stream().map(Note::fromDynamoDb).collect(Collectors.toList())).orElse(null))
            .build();
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
        private List<ApprovalStatus> approvalStatuses;
        private List<Note> notes;

        public Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        @JacocoGenerated
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
                                 publicationDate, isInternationalCollaboration, creatorCount, creators,
                                 approvalStatuses, notes);
        }
    }
}
