package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_DAY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_IDENTITY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MAIN_TITLE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_MONTH;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_NAME;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ORCID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_DATE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_YEAR;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.utils.JsonPointers;
import no.sikt.nva.nvi.index.model.Approval;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.ExpandedResource;
import no.sikt.nva.nvi.index.model.ExpandedResource.Organization;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;

public final class NviCandidateIndexDocumentGenerator {

    private static final int POINTS_SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private NviCandidateIndexDocumentGenerator() {
    }

    public static NviCandidateIndexDocument generateDocument(
        String resource, Candidate candidate) {
        return createNviCandidateIndexDocument(
            attempt(() -> dtoObjectMapper.readTree(resource)).map(root -> root.at(JsonPointers.JSON_PTR_BODY))
                .orElseThrow(), candidate);
    }

    private static NviCandidateIndexDocument createNviCandidateIndexDocument(
        JsonNode resource, Candidate candidate) {
        var approvals = createApprovals(resource, candidate.approvalStatuses());
        return new NviCandidateIndexDocument.Builder()
                   .withContext(URI.create(Contexts.NVI_CONTEXT))
                   .withIdentifier(candidate.identifier().toString())
                   .withApprovals(approvals)
                   .withPublicationDetails(extractPublicationDetails(resource))
                   .withNumberOfApprovals(approvals.size())
                   .withPoints(sumPoints(candidate.candidate().points()))
                   .build();
    }

    private static BigDecimal sumPoints(List<DbInstitutionPoints> points) {
        return points.stream().map(DbInstitutionPoints::points)
                   .reduce(BigDecimal.ZERO, BigDecimal::add)
                   .setScale(POINTS_SCALE, ROUNDING_MODE);
    }

    private static List<Approval> createApprovals(JsonNode resource, List<DbApprovalStatus> approvals) {
        return approvals.stream()
                   .map(approval -> toApproval(resource, approval))
                   .toList();
    }

    private static Map<String, String> extractLabel(JsonNode resource, DbApprovalStatus approval) {
        return getTopLevelOrgs(resource.toString()).stream()
                   .filter(organization -> organization.hasAffiliation(approval.institutionId().toString()))
                   .findFirst()
                   .orElseThrow()
                   .getLabels();
    }

    private static Approval toApproval(JsonNode resource, DbApprovalStatus approval) {
        return new Approval.Builder()
                   .withId(approval.institutionId().toString())
                   .withLabels(extractLabel(resource, approval))
                   .withApprovalStatus(ApprovalStatus.fromValue(approval.status().getValue()))
                   .withAssignee(extractAssignee(approval))
                   .build();
    }

    private static String extractAssignee(DbApprovalStatus approval) {
        return Optional.of(approval)
                   .map(DbApprovalStatus::assignee)
                   .map(DbUsername::value)
                   .orElse(null);
    }

    private static List<Organization> getTopLevelOrgs(String s) {
        return attempt(() -> dtoObjectMapper.readValue(s, ExpandedResource.class)).orElseThrow()
                   .getTopLevelOrganization();
    }

    private static PublicationDetails extractPublicationDetails(JsonNode resource) {
        return new PublicationDetails(extractId(resource), extractInstanceType(resource), extractMainTitle(resource),
                                      extractPublicationDate(resource), extractContributors(resource));
    }

    private static List<Contributor> extractContributors(JsonNode resource) {
        return getJsonNodeStream(resource, JSON_PTR_CONTRIBUTOR)
                   .map(NviCandidateIndexDocumentGenerator::createContributor)
                   .toList();
    }

    private static Contributor createContributor(JsonNode contributor) {
        var identity = contributor.at(JSON_PTR_IDENTITY);
        return new Contributor.Builder()
                   .withId(extractId(identity))
                   .withName(extractJsonNodeTextValue(identity, JSON_PTR_NAME))
                   .withOrcid(extractJsonNodeTextValue(identity, JSON_PTR_ORCID))
                   .withAffiliations(extractAffiliations(contributor))
                   .build();
    }

    private static List<String> extractAffiliations(JsonNode contributor) {
        return streamNode(contributor.at(JSON_PTR_AFFILIATIONS))
                   .map(affiliation -> extractJsonNodeTextValue(affiliation, JSON_PTR_ID))
                   .toList();
    }

    private static String extractId(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_ID);
    }

    private static String extractPublicationDate(JsonNode resource) {
        return formatPublicationDate(resource.at(JSON_PTR_PUBLICATION_DATE));
    }

    private static String extractMainTitle(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_MAIN_TITLE);
    }

    private static String extractInstanceType(JsonNode resource) {
        return extractJsonNodeTextValue(resource, JSON_PTR_INSTANCE_TYPE);
    }

    private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
        return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
    }

    private static String formatPublicationDate(JsonNode publicationDateNode) {
        var year = publicationDateNode.at(JSON_PTR_YEAR);
        var month = publicationDateNode.at(JSON_PTR_MONTH);
        var day = publicationDateNode.at(JSON_PTR_DAY);

        return attempt(() -> LocalDate.of(year.asInt(), month.asInt(), day.asInt()).toString()).orElse(
            failure -> year.textValue());
    }
}
