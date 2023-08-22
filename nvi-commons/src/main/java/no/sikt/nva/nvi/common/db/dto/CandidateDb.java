package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.Typed;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.CandidateDb.Builder;

public class CandidateDb implements WithCopy<Builder>, Typed {
    public static final String TYPE = "Candidate";
    public static final String PUBLICATION_ID_FIELD = "publicationId";
    public static final String PUBLICATION_BUCKET_URI_FIELD = "publicationBucketUri";
    public static final String IS_APPLICABLE_FIELD = "isApplicable";
    public static final String INSTANCE_TYPE_FIELD = "instanceType";
    public static final String LEVEL_FIELD = "level";
    public static final String PUBLICATION_DATE_FIELD = "publicationDate";
    public static final String IS_INTERNAL_COLLABORATION_FIELD = "isInternationalCollaboration";
    public static final String CREATOR_COUNT_FIELD = "creatorCount";
    public static final String CREATORS_FIELD = "creators";
    public static final String APPROVAL_STATUSES_FIELD = "approvalStatuses";
    public static final String NOTES_FIELD = "notes";

    @JsonProperty(PUBLICATION_ID_FIELD)
    private URI publicationId;
    @JsonProperty(PUBLICATION_BUCKET_URI_FIELD)
    private URI publicationBucketUri;
    @JsonProperty(IS_APPLICABLE_FIELD)
    private boolean isApplicable;
    @JsonProperty(INSTANCE_TYPE_FIELD)
    private String instanceType;
    @JsonProperty(LEVEL_FIELD)
    private String level;
    @JsonProperty(PUBLICATION_DATE_FIELD)
    private PublicationDateDb publicationDate;
    @JsonProperty(IS_INTERNAL_COLLABORATION_FIELD)
    private boolean isInternationalCollaboration;
    @JsonProperty(CREATOR_COUNT_FIELD)
    private int creatorCount;
    @JsonProperty(CREATORS_FIELD)
    private List<CreatorDb> creators;
    @JsonProperty(APPROVAL_STATUSES_FIELD)
    private List<ApprovalStatusDb> approvalStatuses;
    @JsonProperty(NOTES_FIELD)
    private List<NoteDb> notes;

    public CandidateDb() {
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void setType(String type) throws IllegalStateException {
        Typed.super.setType(type);
    }

    @Override
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
                   .withApprovalStatuses(approvalStatuses)
                   .withNotes(notes);
    }

    public void setPublicationId(URI publicationId) {
        this.publicationId = publicationId;
    }

    public void setPublicationBucketUri(URI publicationBucketUri) {
        this.publicationBucketUri = publicationBucketUri;
    }

    public void setApplicable(boolean applicable) {
        isApplicable = applicable;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public void setPublicationDate(PublicationDateDb publicationDate) {
        this.publicationDate = publicationDate;
    }

    public void setInternationalCollaboration(boolean internationalCollaboration) {
        isInternationalCollaboration = internationalCollaboration;
    }

    public void setCreatorCount(int creatorCount) {
        this.creatorCount = creatorCount;
    }

    public void setCreators(List<CreatorDb> creators) {
        this.creators = creators;
    }

    public void setApprovalStatuses(List<ApprovalStatusDb> approvalStatuses) {
        this.approvalStatuses = approvalStatuses;
    }

    public void setNotes(List<NoteDb> notes) {
        this.notes = notes;
    }

    public URI getPublicationId() {
        return publicationId;
    }

    public URI getPublicationBucketUri() {
        return publicationBucketUri;
    }

    public boolean isApplicable() {
        return isApplicable;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public String getLevel() {
        return level;
    }

    public PublicationDateDb getPublicationDate() {
        return publicationDate;
    }

    public boolean isInternationalCollaboration() {
        return isInternationalCollaboration;
    }

    public int getCreatorCount() {
        return creatorCount;
    }

    public List<CreatorDb> getCreators() {
        return creators;
    }

    public List<ApprovalStatusDb> getApprovalStatuses() {
        return approvalStatuses;
    }

    public List<NoteDb> getNotes() {
        return notes;
    }

    public static final class Builder {

        private CandidateDb candidateDb;

        public Builder() {
            this.candidateDb = new CandidateDb();
        }

        public Builder withPublicationId(URI publicationId) {
            this.candidateDb.setPublicationId(publicationId);
            return this;
        }
        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.candidateDb.setPublicationBucketUri(publicationBucketUri);
            return this;
        }
        public Builder withIsApplicable(boolean isApplicable) {
            this.candidateDb.setApplicable(isApplicable);
            return this;
        }
        public Builder withInstanceType(String instanceType) {
            this.candidateDb.setInstanceType(instanceType);
            return this;
        }
        public Builder withLevel(String level) {
            this.candidateDb.setLevel(level);
            return this;
        }
        public Builder withPublicationDate(PublicationDateDb publicationDate) {
            this.candidateDb.setPublicationDate(publicationDate);
            return this;
        }
        public Builder withIsInternationalCollaboration(boolean isInternationalCollaboration) {
            this.candidateDb.setInternationalCollaboration(isInternationalCollaboration);
            return this;
        }
        public Builder withCreatorCount(int creatorCount) {
            this.candidateDb.setCreatorCount(creatorCount);
            return this;
        }
        public Builder withCreators(List<CreatorDb> creators) {
            this.candidateDb.setCreators(creators);
            return this;
        }
        public Builder withApprovalStatuses(List<ApprovalStatusDb> approvalStatuses) {
            this.candidateDb.setApprovalStatuses(approvalStatuses);
            return this;
        }
        public Builder withNotes(List<NoteDb> notes) {
            this.candidateDb.setNotes(notes);
            return this;
        }

        public CandidateDb build() {
            return candidateDb;
        }
    }
}
