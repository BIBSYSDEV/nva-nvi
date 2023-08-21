package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import no.sikt.nva.nvi.common.db.Typed;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.VerifiedCreator;

public class CandidateDbDto implements Typed {

    public static final String TYPE = "Candidate";

    public static final String PUBLICATION_ID_FIELD = "publicationId";
    public static final String PERIOD_FIELD = "period";
    public static final String IS_APPLICABLE_FIELD = "isApplicable";
    public static final String INSTANCE_TYPE_FIELD = "instanceType";
    public static final String LEVEL_FIELD = "level";
    public static final String PUBLICATION_DATE_FIELD = "publicationDate";
    public static final String IS_INTERNAL_COLLABORATION_FIELD = "isInternationalCollaboration";
    public static final String CREATOR_COUNT_FIELD = "publicationId";
    public static final String CREATORS_FIELD = "publicationId";
    public static final String APPROVAL_STATUSES_FIELD = "publicationId";
    public static final String NOTES_FIELD = "publicationId";

    @JsonProperty(PUBLICATION_ID_FIELD)
    private URI publicationId;
    @JsonProperty(PERIOD_FIELD)
    private PeriodDbDto period;
    @JsonProperty(IS_APPLICABLE_FIELD)
    private boolean isApplicable;
    @JsonProperty(INSTANCE_TYPE_FIELD)
    private String instanceType;
    @JsonProperty(LEVEL_FIELD)
    private String level;
    @JsonProperty(PUBLICATION_DATE_FIELD)
    private PublicationDateDbDto publicationDate;
    @JsonProperty(IS_INTERNAL_COLLABORATION_FIELD)
    private boolean isInternationalCollaboration;
    @JsonProperty(CREATOR_COUNT_FIELD)
    private int creatorCount;
    @JsonProperty(CREATORS_FIELD)
    private List<VerifiedCreatorDbDto> creators;
    @JsonProperty(APPROVAL_STATUSES_FIELD)
    private List<ApprovalStatusDbDto> approvalStatuses;
    @JsonProperty(NOTES_FIELD)
    private List<NoteDbDto> notes;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void setType(String type) throws IllegalStateException {
        Typed.super.setType(type);
    }
}
