package no.sikt.nva.nvi.events.cristin;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.events.evaluator.model.InstanceType;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public final class CristinMapper {

    public static final String API_HOST = new Environment().readEnv("API_HOST");
    public static final String PERSISTED_RESOURCES_BUCKET = new Environment().readEnv("EXPANDED_RESOURCES_BUCKET");
    public static final String AFFILIATION_DELIMITER = ".";
    public static final String CRISTIN = "cristin";
    public static final String ORGANIZATION = "organization";
    public static final String GZ_EXTENSION = ".gz";
    public static final String PUBLICATION = "publication";
    public static final String RESOURCES = "resources";
    public static final String PERSON = "person";
    public static final String PARENT_PUBLICATION_SERIES_LEVEL_JSON_POINTER = "/publicationContext/entityDescription"
                                                                              + "/reference/publicationContext/series"
                                                                              + "/level";
    public static final String PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER =
        "/publicationContext/entityDescription/reference"
        + "/publicationContext/series/scientificValue";
    public static final String PARENT_PUBLICATION_SERIES_ID_JSON_POINTER = "/publicationContext/entityDescription"
                                                                           + "/reference/publicationContext/series/id";
    public static final String PARENT_PUBLICATION_PUBLISHER_ID_JSON_POINTER = "/publicationContext/entityDescription"
                                                                              + "/reference/publicationContext"
                                                                              + "/publisher/id";
    public static final String PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER = "/publicationContext/entityDescription"
                                                                             + "/reference/publicationContext/series"
                                                                             + "/type";
    public static final String PARENT_PUBLICATION_PUBLISHER_TYPE_JSON_POINTER = "/publicationContext"
                                                                                + "/entityDescription/reference"
                                                                                + "/publicationContext/publisher/type";
    public static final String SERIES_ID_JSON_POINTER = "/publicationContext/series/id";
    public static final String PUBLISHER_ID_JSON_POINTER = "/publicationContext/publisher/id";
    public static final String SERIES_TYPE_JSON_POINTER = "/publicationContext/series/type";
    public static final String PUBLISHER_TYPE_JSON_POINTER = "/publicationContext/publisher/type";
    public static final String PUBLICATION_CONTEXT_ID_JSON_POINTER = "/publicationContext/id";
    public static final String PUBLICATION_CONTEXT_TYPE_JSON_POINTER = "/publicationContext/type";
    private static final String INTERNATIONAL_COLLABORATION_FACTOR = "1.3";

    private CristinMapper() {

    }

    //TODO: Extract creators from dbh_forskres_kontroll, remove Jacoco annotation when implemented
    public static DbCandidate toDbCandidate(CristinNviReport cristinNviReport) {
        var now = Instant.now();
        var points = calculatePoints(cristinNviReport);
        return DbCandidate.builder()
                   .publicationId(constructPublicationId(cristinNviReport.publicationIdentifier()))
                   .publicationBucketUri(constructPublicationBucketUri(cristinNviReport.publicationIdentifier()))
                   .publicationDate(constructPublicationDate(cristinNviReport.publicationDate()))
                   .instanceType(cristinNviReport.instanceType())
                   .level(extractLevel(cristinNviReport))
                   .reportStatus(ReportStatus.REPORTED)
                   .applicable(true)
                   .createdDate(now)
                   .modifiedDate(now)
                   .points(points)
                   .totalPoints(sumPoints(points))
                   .basePoints(extractBasePoints(cristinNviReport))
                   .collaborationFactor(extractCollaborationFactor(cristinNviReport))
                   .internationalCollaboration(isInternationalCollaboration(cristinNviReport))
                   .creators(extractCreators(cristinNviReport))
//                   .creatorCount()
//                   .creatorShareCount()
                   .channelId(extractChannelId(cristinNviReport))
                   .channelType(extractChannelType(cristinNviReport))
                   .build();
    }

    public static List<DbCreator> extractCreators(CristinNviReport cristinNviReport) {
        return getCreators(cristinNviReport).stream()
                   .filter(CristinMapper::hasInstitutionPoints)
                   .collect(groupByCristinIdentifierAndMapToAffiliationId())
                   .entrySet()
                   .stream()
                   .map(CristinMapper::toDbCreator)
                   .toList();
    }

    public static List<DbApprovalStatus> toApprovals(CristinNviReport cristinNviReport) {
        return cristinNviReport.cristinLocales().stream().map(CristinMapper::toApproval).toList();
    }

    private static BigDecimal sumPoints(List<DbInstitutionPoints> points) {
        return points.stream().map(DbInstitutionPoints::points)
                   .reduce(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private static ChannelType extractChannelType(CristinNviReport cristinNviReport) {
        var instance = toInstanceType(cristinNviReport.instanceType());
        var referenceNode = cristinNviReport.reference();
        if (nonNull(instance)) {
            var channelType = switch (instance) {
                case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                    extractJsonNodeTextValue(referenceNode, PUBLICATION_CONTEXT_TYPE_JSON_POINTER);
                case ACADEMIC_MONOGRAPH -> extractChannelTypeForAcademicMonograph(referenceNode);
                case ACADEMIC_CHAPTER -> extractChannelTypeForAcademicChapter(referenceNode);
            };
            return ChannelType.parse(channelType);
        }
        return null;
    }

    private static URI extractChannelId(CristinNviReport cristinNviReport) {
        var instance = toInstanceType(cristinNviReport.instanceType());
        var referenceNode = cristinNviReport.reference();
        if (nonNull(instance)) {
            var channelId = switch (instance) {
                case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                    extractJsonNodeTextValue(referenceNode, PUBLICATION_CONTEXT_ID_JSON_POINTER);
                case ACADEMIC_MONOGRAPH -> extractChannelIdForAcademicMonograph(referenceNode);
                case ACADEMIC_CHAPTER -> extractChannelIdForAcademicChapter(referenceNode);
            };
            return attempt(() -> UriWrapper.fromUri(channelId).getUri()).orElse(failure -> null);
        }
        return null;
    }

    private static InstanceType toInstanceType(String instanceType) {
        return attempt(() -> InstanceType.parse(instanceType)).orElse(failure -> null);
    }

    private static String extractChannelIdForAcademicChapter(JsonNode referenceNode) {
        if (nonNull(extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_LEVEL_JSON_POINTER)) || nonNull(
            extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER))) {
            return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_ID_JSON_POINTER);
        } else {
            return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_PUBLISHER_ID_JSON_POINTER);
        }
    }

    private static String extractChannelTypeForAcademicChapter(JsonNode referenceNode) {
        if (nonNull(extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER)) || nonNull(
            extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER))) {
            return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER);
        } else {
            return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_PUBLISHER_TYPE_JSON_POINTER);
        }
    }

    private static String extractChannelIdForAcademicMonograph(JsonNode referenceNode) {
        return nonNull(extractJsonNodeTextValue(referenceNode, SERIES_ID_JSON_POINTER))
                   ? extractJsonNodeTextValue(referenceNode, SERIES_ID_JSON_POINTER)
                   : extractJsonNodeTextValue(referenceNode,PUBLISHER_ID_JSON_POINTER);
    }

    private static String extractChannelTypeForAcademicMonograph(JsonNode referenceNode) {
        return nonNull(extractJsonNodeTextValue(referenceNode, SERIES_TYPE_JSON_POINTER))
                   ? extractJsonNodeTextValue(referenceNode,SERIES_TYPE_JSON_POINTER)
                   : extractJsonNodeTextValue(referenceNode, PUBLISHER_TYPE_JSON_POINTER);
    }

    private static boolean isInternationalCollaboration(CristinNviReport cristinNviReport) {
        return cristinNviReport.scientificResources()
                   .get(0)
                   .getCreators()
                   .stream()
                   .map(ScientificPerson::getCollaborationFactor)
                   .filter(Objects::nonNull)
                   .findFirst()
                   .map(INTERNATIONAL_COLLABORATION_FACTOR::equals)
                   .orElse(false);
    }

    private static BigDecimal extractCollaborationFactor(CristinNviReport cristinNviReport) {
        return cristinNviReport.scientificResources()
                   .get(0)
                   .getCreators()
                   .stream()
                   .map(ScientificPerson::getCollaborationFactor)
                   .filter(Objects::nonNull)
                   .map(BigDecimal::new)
                   .map(bigDecimal -> bigDecimal.setScale(4, RoundingMode.HALF_UP))
                   .findFirst()
                   .orElse(null);
    }

    private static BigDecimal extractBasePoints(CristinNviReport cristinNviReport) {
        return cristinNviReport.scientificResources()
                   .get(0)
                   .getCreators()
                   .stream()
                   .map(ScientificPerson::getPublicationTypeLevelPoints)
                   .filter(Objects::nonNull)
                   .map(BigDecimal::new)
                   .map(bigDecimal -> bigDecimal.setScale(4, RoundingMode.HALF_UP))
                   .findFirst()
                   .orElseThrow();
    }

    private static List<DbInstitutionPoints> calculatePoints(CristinNviReport cristinNviReport) {
        var institutions = cristinNviReport.cristinLocales();
        return getCreators(cristinNviReport).stream()
                   .filter(CristinMapper::hasInstitutionPoints)
                   .collect(collectToMapOfPoints(institutions))
                   .entrySet()
                   .stream()
                   .map(CristinMapper::toDbInstitutionPoints)
                   .collect(Collectors.toList());
    }

    private static boolean hasInstitutionPoints(ScientificPerson scientificPerson) {
        return nonNull(scientificPerson.getAuthorPointsForAffiliation());
    }

    private static DbInstitutionPoints toDbInstitutionPoints(Entry<URI, BigDecimal> entry) {
        //TODO: Implement creatorAffiliationPoints
        return new DbInstitutionPoints(entry.getKey(), entry.getValue(), List.of());
    }

    private static List<ScientificPerson> getCreators(CristinNviReport cristinNviReport) {
        return cristinNviReport.scientificResources().get(0).getCreators();
    }

    private static Collector<ScientificPerson, ?, Map<URI, BigDecimal>> collectToMapOfPoints(
        List<CristinLocale> institutions) {
        return Collectors.groupingBy(scientificPerson -> getTopLevelOrganization(scientificPerson, institutions),
                                     Collectors.reducing(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                                                         CristinMapper::extractAuthorPointsForAffiliation,
                                                         BigDecimal::add));
    }

    private static BigDecimal extractAuthorPointsForAffiliation(ScientificPerson scientificPerson) {
        return new BigDecimal(scientificPerson.getAuthorPointsForAffiliation());
    }

    private static URI getTopLevelOrganization(ScientificPerson scientificPerson, List<CristinLocale> institutions) {
        return institutions.stream()
                   .filter(cristinLocale -> cristinLocale.getInstitutionIdentifier()
                                                .equals(scientificPerson.getInstitutionIdentifier()))
                   .map(CristinMapper::constructInstitutionId)
                   .toList()
                   .get(0);
    }

    @JacocoGenerated
    private static DbCreator toDbCreator(Entry<URI, List<URI>> entry) {
        return DbCreator.builder().creatorId(entry.getKey()).affiliations(entry.getValue()).build();
    }

    @JacocoGenerated
    private static Collector<ScientificPerson, ?, Map<URI, List<URI>>> groupByCristinIdentifierAndMapToAffiliationId() {
        return Collectors.groupingBy(CristinMapper::constructPersonCristinId,
                                     Collectors.mapping(CristinMapper::constructCristinOrganizationId,
                                                        Collectors.toList()));
    }

    @JacocoGenerated
    private static URI constructPersonCristinId(ScientificPerson scientificPerson) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(CRISTIN)
                   .addChild(PERSON)
                   .addChild(scientificPerson.getCristinPersonIdentifier())
                   .getUri();
    }

    @JacocoGenerated
    private static URI constructCristinOrganizationId(ScientificPerson scientificPerson) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(CRISTIN)
                   .addChild(ORGANIZATION)
                   .addChild(scientificPerson.getOrganization())
                   .getUri();
    }

    private static DbLevel extractLevel(CristinNviReport cristinNviReport) {
        return Optional.ofNullable(cristinNviReport.scientificResources())
                   .map(list -> list.get(0))
                   .map(ScientificResource::getQualityCode)
                   .map(DbLevel::fromDeprecatedValue)
                   .orElseThrow();
    }

    private static DbApprovalStatus toApproval(CristinLocale cristinLocale) {
        var assignee = constructUsername(cristinLocale);
        return DbApprovalStatus.builder()
                   .status(DbStatus.APPROVED)
                   .institutionId(constructInstitutionId(cristinLocale))
                   .finalizedDate(constructFinalizedDate(cristinLocale))
                   .finalizedBy(assignee)
                   .assignee(assignee)
                   .build();
    }

    private static Username constructUsername(CristinLocale cristinLocale) {
        var userIdentifier = extractAssigneeIdentifier(cristinLocale);
        return nonNull(userIdentifier) ? Username.fromString(constructUsername(cristinLocale, userIdentifier)) : null;
    }

    private static String constructUsername(CristinLocale cristinLocale, String userIdentifier) {
        return String.format("%s@%s", userIdentifier, constructInstitutionIdentifier(cristinLocale));
    }

    private static String extractAssigneeIdentifier(CristinLocale cristinLocale) {
        return Optional.ofNullable(cristinLocale)
                   .map(CristinLocale::getControlledByUser)
                   .map(CristinUser::getIdentifier)
                   .orElse(null);
    }

    private static Instant constructFinalizedDate(CristinLocale cristinLocale) {
        return Optional.ofNullable(cristinLocale)
                   .map(CristinLocale::getDateControlled)
                   .map(LocalDate::atStartOfDay)
                   .map(localDateTime -> localDateTime.toInstant(zoneOffset()))
                   .orElse(null);
    }

    private static ZoneOffset zoneOffset() {
        return ZoneOffset.UTC.getRules().getOffset(Instant.now());
    }

    private static URI constructInstitutionId(CristinLocale cristinLocale) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(CRISTIN)
                   .addChild(ORGANIZATION)
                   .addChild(constructInstitutionIdentifier(cristinLocale))
                   .getUri();
    }

    private static String constructInstitutionIdentifier(CristinLocale cristinLocale) {
        return cristinLocale.getInstitutionIdentifier()
               + AFFILIATION_DELIMITER
               + cristinLocale.getDepartmentIdentifier()
               + AFFILIATION_DELIMITER
               + cristinLocale.getSubDepartmentIdentifier()
               + AFFILIATION_DELIMITER
               + cristinLocale.getGroupIdentifier();
    }

    private static DbPublicationDate constructPublicationDate(PublicationDate publicationDate) {
        return DbPublicationDate.builder()
                   .day(publicationDate.day())
                   .month(publicationDate.month())
                   .year(publicationDate.year())
                   .build();
    }

    private static URI constructPublicationBucketUri(String publicationIdentifier) {
        return UriWrapper.fromHost(PERSISTED_RESOURCES_BUCKET)
                   .addChild(RESOURCES)
                   .addChild(withGzExtension(publicationIdentifier))
                   .getUri();
    }

    private static String withGzExtension(String publicationIdentifier) {
        return publicationIdentifier + GZ_EXTENSION;
    }

    private static URI constructPublicationId(String publicationIdentifier) {
        return UriWrapper.fromHost(API_HOST).addChild(PUBLICATION).addChild(publicationIdentifier).getUri();
    }
}
