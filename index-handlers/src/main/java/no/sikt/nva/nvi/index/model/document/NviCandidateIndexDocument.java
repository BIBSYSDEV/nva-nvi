package no.sikt.nva.nvi.index.model.document;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.ExceptionUtils.getStackTrace;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_FIRST_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_IDENTIFIER;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_LAST_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CREATOR_SHARE_COUNT;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.DEPARTMENT_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.FACULTY_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.GLOBAL_STATUS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.GROUP_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PAGE_BEGIN;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PAGE_COUNT;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PAGE_END;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.POINTS_FOR_AFFILIATION;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL_POINTS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_TYPE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_IDENTIFIER;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_INSTANCE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_LANGUAGE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_TITLE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLISHED_YEAR;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.REPORTING_YEAR;
import static nva.commons.core.StringUtils.EMPTY_STRING;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.report.InstitutionReportHeader;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonSerialize
public record NviCandidateIndexDocument(@JsonProperty(CONTEXT) URI context,
                                        URI id,
                                        boolean isApplicable,
                                        String type,
                                        UUID identifier,
                                        PublicationDetails publicationDetails,
                                        List<Approval> approvals,
                                        int numberOfApprovals,
                                        BigDecimal points,
                                        BigDecimal publicationTypeChannelLevelPoints,
                                        GlobalApprovalStatus globalApprovalStatus,
                                        int creatorShareCount,
                                        BigDecimal internationalCollaborationFactor,
                                        ReportingPeriod reportingPeriod,
                                        boolean reported,
                                        String createdDate,
                                        String modifiedDate) implements JsonSerializable {

    private static final Logger logger = LoggerFactory.getLogger(NviCandidateIndexDocument.class);
    private static final String CONTEXT = "@context";
    private static final String NVI_CANDIDATE = "NviCandidate";
    private static final String TYPE = NVI_CANDIDATE;
    private static final String REPORT_REJECTED_VALUE = "N";
    private static final String REPORT_PENDING_VALUE = "?";
    private static final String REPORT_APPROVED_VALUE = "J";
    private static final String REPORT_DISPUTED_VALUE = "T";
    private static final String UNSUPPORTED_LANGUAGE = "Annet språk";

    public static NviCandidateIndexDocument from(JsonNode expandedResource, Candidate candidate,
                                                 UriRetriever uriRetriever) {
        return new NviCandidateIndexDocumentGenerator(uriRetriever, expandedResource, candidate).generateDocument();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Approval getApprovalForInstitution(URI institutionId) {
        return approvals.stream()
                   .filter(approval -> approval.institutionId().equals(institutionId))
                   .findAny()
                   .orElseThrow();
    }

    public BigDecimal getPointsForContributorAffiliation(URI topLevelCristinOrg,
                                                         NviContributor nviContributor,
                                                         NviOrganization affiliation) {
        return getApprovalForInstitution(topLevelCristinOrg).getPointsForAffiliation(nviContributor, affiliation);
    }

    @JsonIgnore
    public String publicationIdentifier() {
        return UriWrapper.fromUri(publicationDetails.id()).getLastPathElement();
    }

    public ApprovalStatus getApprovalStatusForInstitution(URI topLevelCristinOrg) {
        return getApprovalForInstitution(topLevelCristinOrg).approvalStatus();
    }

    @JsonIgnore
    public List<NviContributor> getNviContributors() {
        return publicationDetails.nviContributors();
    }

    public List<Map<InstitutionReportHeader, String>> toReportRowsForInstitution(URI topLevelOrganization) {
        return getNviContributors().stream()
                   .flatMap(
                       nviContributor -> generateRowsForContributorAffiliations(nviContributor, topLevelOrganization))
                   .toList();
    }

    private Stream<Map<InstitutionReportHeader, String>> generateRowsForContributorAffiliations(
        NviContributor nviContributor, URI topLevelOrganization) {
        return nviContributor.getAffiliationsPartOfOrEqualTo(topLevelOrganization)
                   .map(affiliation -> generateRow(nviContributor, affiliation, topLevelOrganization));
    }

    private Map<InstitutionReportHeader, String> generateRow(NviContributor nviContributor, NviOrganization affiliation,
                                                             URI topLevelOrganization) {
        try {
            var keyValueMap = new HashMap<InstitutionReportHeader, String>();
            keyValueMap.put(REPORTING_YEAR, reportingPeriod.year());
            keyValueMap.put(PUBLICATION_IDENTIFIER, publicationIdentifier());
            keyValueMap.put(PUBLISHED_YEAR, publicationDetails.publicationDate().year());
            keyValueMap.put(INSTITUTION_APPROVAL_STATUS, getApprovalStatus(topLevelOrganization));
            keyValueMap.put(PUBLICATION_INSTANCE, publicationDetails.type());
            keyValueMap.put(CONTRIBUTOR_IDENTIFIER, nviContributor.id());
            keyValueMap.put(INSTITUTION_ID, affiliation.getInstitutionIdentifier());
            keyValueMap.put(FACULTY_ID, affiliation.getFacultyIdentifier());
            keyValueMap.put(DEPARTMENT_ID, affiliation.getDepartmentIdentifier());
            keyValueMap.put(GROUP_ID, affiliation.getGroupIdentifier());
            keyValueMap.put(CONTRIBUTOR_LAST_NAME, nviContributor.name()); //We don't have a good way to split the name
            keyValueMap.put(CONTRIBUTOR_FIRST_NAME, nviContributor.name()); //For now, use the full name
            keyValueMap.put(PUBLICATION_TITLE, publicationDetails.title());
            keyValueMap.put(GLOBAL_STATUS, getGlobalApprovalStatus());
            keyValueMap.put(PUBLICATION_CHANNEL_LEVEL_POINTS, publicationTypeChannelLevelPoints().toString());
            keyValueMap.put(INTERNATIONAL_COLLABORATION_FACTOR, internationalCollaborationFactor().toString());
            keyValueMap.put(CREATOR_SHARE_COUNT, String.valueOf(creatorShareCount()));
            keyValueMap.put(POINTS_FOR_AFFILIATION,
                            getPointsForContributorAffiliation(topLevelOrganization, nviContributor, affiliation)
                                .toString());
            keyValueMap.put(PUBLICATION_LANGUAGE, getLanguageLabel());
            addOptionalPublicationChannelValues(keyValueMap);
            addOptionalPages(keyValueMap);

            return keyValueMap;
        } catch (RuntimeException exception) {
            logger.error("Failed to generate report lines for candidate: {}. Error {}", id, getStackTrace(exception));
            throw exception;
        }
    }

    private String getLanguageLabel() {
        return nonNull(publicationDetails.language())
                   ? LanguageLabelUtil.getLabel(publicationDetails.language()).orElse(UNSUPPORTED_LANGUAGE)
                   : EMPTY_STRING;
    }

    private void addOptionalPublicationChannelValues(Map<InstitutionReportHeader, String> keyValueMap) {
        var publicationChannel = publicationDetails.publicationChannel();
        keyValueMap.put(PUBLICATION_CHANNEL, nonNull(publicationChannel.id()) ? publicationChannel.id().toString()
                                                 : EMPTY_STRING);
        keyValueMap.put(PUBLICATION_CHANNEL_LEVEL, publicationChannel.scientificValue().getValue());
        keyValueMap.put(PUBLICATION_CHANNEL_TYPE,
                        nonNull(publicationChannel.type()) ? publicationChannel.type() : EMPTY_STRING);
        keyValueMap.put(PUBLICATION_CHANNEL_NAME,
                        nonNull(publicationChannel.name()) ? publicationChannel.name() : EMPTY_STRING);
    }

    private void addOptionalPages(Map<InstitutionReportHeader, String> keyValueMap) {
        var pages = publicationDetails.pages();
        keyValueMap.put(PAGE_BEGIN, nonNull(pages) && nonNull(pages.begin()) ? pages.begin() : EMPTY_STRING);
        keyValueMap.put(PAGE_END, nonNull(pages) && nonNull(pages.end()) ? pages.end() : EMPTY_STRING);
        keyValueMap.put(PAGE_COUNT,
                        nonNull(pages) && nonNull(pages.numberOfPages()) ? pages.numberOfPages() : EMPTY_STRING);
    }

    private String getGlobalApprovalStatus() {
        return switch (globalApprovalStatus) {
            case PENDING -> REPORT_PENDING_VALUE;
            case APPROVED -> REPORT_APPROVED_VALUE;
            case REJECTED -> REPORT_REJECTED_VALUE;
            case DISPUTE -> REPORT_DISPUTED_VALUE;
        };
    }

    private String getApprovalStatus(URI topLevelOrganization) {
        return switch (getApprovalStatusForInstitution(topLevelOrganization)) {
            case APPROVED -> REPORT_APPROVED_VALUE;
            case REJECTED -> REPORT_REJECTED_VALUE;
            case NEW, PENDING -> REPORT_PENDING_VALUE;
        };
    }

    public static final class Builder {

        private URI context;
        private URI id;
        private boolean isApplicable;
        private UUID identifier;
        private PublicationDetails publicationDetails;
        private List<Approval> approvals;
        private int numberOfApprovals;
        private BigDecimal points;
        private BigDecimal publicationTypeChannelLevelPoints;
        private GlobalApprovalStatus globalApprovalStatus;
        private int creatorShareCount;
        private BigDecimal internationalCollaborationFactor;
        private ReportingPeriod reportingPeriod;
        private boolean reported;
        private String createdDate;
        private String modifiedDate;

        private Builder() {
        }

        public Builder withContext(URI context) {
            this.context = context;
            return this;
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withIsApplicable(boolean isApplicable) {
            this.isApplicable = isApplicable;
            return this;
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withPublicationDetails(PublicationDetails publicationDetails) {
            this.publicationDetails = publicationDetails;
            return this;
        }

        public Builder withApprovals(List<Approval> approvals) {
            this.approvals = approvals;
            return this;
        }

        public Builder withNumberOfApprovals(int numberOfApprovals) {
            this.numberOfApprovals = numberOfApprovals;
            return this;
        }

        public Builder withPoints(BigDecimal points) {
            this.points = points;
            return this;
        }

        public Builder withPublicationTypeChannelLevelPoints(BigDecimal publicationTypeChannelLevelPoints) {
            this.publicationTypeChannelLevelPoints = publicationTypeChannelLevelPoints;
            return this;
        }

        public Builder withGlobalApprovalStatus(GlobalApprovalStatus globalApprovalStatus) {
            this.globalApprovalStatus = globalApprovalStatus;
            return this;
        }

        public Builder withCreatorShareCount(int creatorShareCount) {
            this.creatorShareCount = creatorShareCount;
            return this;
        }

        public Builder withInternationalCollaborationFactor(BigDecimal internationalCollaborationFactor) {
            this.internationalCollaborationFactor = internationalCollaborationFactor;
            return this;
        }

        public Builder withReportingPeriod(ReportingPeriod reportingPeriod) {
            this.reportingPeriod = reportingPeriod;
            return this;
        }

        public Builder withReported(boolean reported) {
            this.reported = reported;
            return this;
        }

        public Builder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate.toString();
            return this;
        }

        public Builder withModifiedDate(Instant modifiedDate) {
            this.modifiedDate = modifiedDate.toString();
            return this;
        }

        public NviCandidateIndexDocument build() {
            return new NviCandidateIndexDocument(context, id, isApplicable, TYPE, identifier, publicationDetails,
                                                 approvals, numberOfApprovals, points,
                                                 publicationTypeChannelLevelPoints, globalApprovalStatus,
                                                 creatorShareCount, internationalCollaborationFactor, reportingPeriod,
                                                 reported, createdDate, modifiedDate);
        }
    }
}
