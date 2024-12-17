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
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.events.cristin.InstitutionPoints.CreatorPoints;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import nva.commons.core.paths.UriWrapper;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
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
    public static final String FHI_CRISTIN_IDENTIFIER = "7502.0.0.0";
    private static final String SERIES_ID_JSON_POINTER = "/publicationContext/series/id";
    private static final String ENTITY_DESCRIPTION = "/publicationContext/entityDescription";
    private static final String PARENT_PUBLICATION_SERIES_LEVEL_JSON_POINTER = ENTITY_DESCRIPTION
                                                                               + "/reference/publicationContext/series"
                                                                               + "/level";
    private static final String PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER =
        "/publicationContext/entityDescription/reference"
        + "/publicationContext/series/scientificValue";
    private static final String PARENT_PUBLICATION_SERIES_ID_JSON_POINTER = ENTITY_DESCRIPTION
                                                                            + "/reference/publicationContext/series/id";
    private static final String PARENT_PUBLICATION_PUBLISHER_ID_JSON_POINTER = ENTITY_DESCRIPTION
                                                                               + "/reference/publicationContext"
                                                                               + "/publisher/id";
    private static final String PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER = ENTITY_DESCRIPTION
                                                                              + "/reference/publicationContext/series"
                                                                              + "/type";
    private static final String PARENT_PUBLICATION_PUBLISHER_TYPE_JSON_POINTER = ENTITY_DESCRIPTION + "/reference"
                                                                                 + "/publicationContext/publisher/type";
    private static final String PUBLISHER_ID_JSON_POINTER = "/publicationContext/publisher/id";
    private static final String SERIES_TYPE_JSON_POINTER = "/publicationContext/series/type";
    private static final String PUBLISHER_TYPE_JSON_POINTER = "/publicationContext/publisher/type";
    private static final String PUBLICATION_CONTEXT_ID_JSON_POINTER = "/publicationContext/id";
    private static final String PUBLICATION_CONTEXT_TYPE_JSON_POINTER = "/publicationContext/type";
    private static final String KREFTREG = "KREFTREG";
    private static final String FHI_CRISTIN_ORG_NUMBER = "7502";
    private static final String INTERNATIONAL_COLLABORATION_FACTOR = "1.3";
    private final List<CristinDepartmentTransfer> departmentTransfers;

    private CristinMapper(List<CristinDepartmentTransfer> transfers) {
        this.departmentTransfers = transfers;
    }

    public static CristinMapper withDepartmentTransfers(List<CristinDepartmentTransfer> transfers) {
        return new CristinMapper(transfers);
    }

    public static List<DbCreator> extractCreators(CristinNviReport cristinNviReport) {
        return cristinNviReport.getCreators().stream()
                   .filter(CristinMapper::hasInstitutionPoints)
                   .collect(groupByCristinIdentifierAndMapToAffiliationId())
                   .entrySet()
                   .stream()
                   .map(CristinMapper::toDbCreator)
                   .toList();
    }

    //TODO: Extract creators from dbh_forskres_kontroll, remove Jacoco annotation when implemented
    public DbCandidate toDbCandidate(CristinNviReport cristinNviReport) {
        var now = Instant.now();
        var points = calculatePoints(cristinNviReport);
        var creators = extractCreators(cristinNviReport).stream()
                                   .map(creator -> (DbCreatorType) creator)
                                   .toList();
        return DbCandidate.builder()
                   .publicationId(constructPublicationId(cristinNviReport.publicationIdentifier()))
                   .publicationBucketUri(constructPublicationBucketUri(cristinNviReport.publicationIdentifier()))
                   .publicationDate(constructPublicationDate(cristinNviReport.publicationDate()))
                   .instanceType(cristinNviReport.instanceType())
                   .level(cristinNviReport.getLevel())
                   .reportStatus(ReportStatus.REPORTED)
                   .applicable(true)
                   .createdDate(now)
                   .modifiedDate(now)
                   .points(points)
                   .totalPoints(sumPoints(points))
                   .basePoints(extractBasePoints(cristinNviReport))
                   .collaborationFactor(extractCollaborationFactor(cristinNviReport))
                   .internationalCollaboration(isInternationalCollaboration(cristinNviReport))
                   .creators(creators)
                   //                   .creatorCount()
                   //                   .creatorShareCount()
                   .channelId(extractChannelId(cristinNviReport))
                   .channelType(extractChannelType(cristinNviReport))
                   .build();
    }

    public List<DbApprovalStatus> toApprovals(CristinNviReport cristinNviReport) {
        return cristinNviReport.cristinLocales().stream()
                   .filter(
                       cristinLocale -> nonNull(cristinLocale.getInstitutionIdentifier()) || isKreftReg(cristinLocale))
                   .map(CristinMapper::toApproval).toList();
    }

    private static Boolean isKreftReg(CristinLocale cristinLocale) {
        return Optional.ofNullable(cristinLocale.getOwnerCode())
                   .map(KREFTREG::equals).orElse(false);
    }

    private static BigDecimal sumPoints(List<DbInstitutionPoints> points) {
        return points.stream()
                   .map(DbInstitutionPoints::points)
                   .reduce(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private static ChannelType extractChannelType(CristinNviReport cristinNviReport) {
        var instance = toInstanceType(cristinNviReport.instanceType());
        var referenceNode = cristinNviReport.reference();
        if (nonNull(instance)) {
            var channelType = switch (instance) {
                case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                    extractJsonNodeTextValue(referenceNode, PUBLICATION_CONTEXT_TYPE_JSON_POINTER);
                case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY -> extractChannelTypeForAcademicMonograph(referenceNode);
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
                case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY -> extractChannelIdForAcademicMonograph(referenceNode);
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
                   : extractJsonNodeTextValue(referenceNode, PUBLISHER_ID_JSON_POINTER);
    }

    private static String extractChannelTypeForAcademicMonograph(JsonNode referenceNode) {
        return nonNull(extractJsonNodeTextValue(referenceNode, SERIES_TYPE_JSON_POINTER))
                   ? extractJsonNodeTextValue(referenceNode, SERIES_TYPE_JSON_POINTER)
                   : extractJsonNodeTextValue(referenceNode, PUBLISHER_TYPE_JSON_POINTER);
    }

    private static boolean isInternationalCollaboration(CristinNviReport cristinNviReport) {
        return cristinNviReport.getCreators()
                   .stream()
                   .map(ScientificPerson::getCollaborationFactor)
                   .filter(Objects::nonNull)
                   .findFirst()
                   .map(INTERNATIONAL_COLLABORATION_FACTOR::equals)
                   .orElse(false);
    }

    private static BigDecimal extractCollaborationFactor(CristinNviReport cristinNviReport) {
        return cristinNviReport.getCreators()
                   .stream()
                   .map(ScientificPerson::getCollaborationFactor)
                   .filter(Objects::nonNull)
                   .map(BigDecimal::new)
                   .map(bigDecimal -> bigDecimal.setScale(4, RoundingMode.HALF_UP))
                   .findFirst()
                   .orElse(null);
    }

    private static BigDecimal extractBasePoints(CristinNviReport cristinNviReport) {
        return cristinNviReport.getCreators()
                   .stream()
                   .map(ScientificPerson::getPublicationTypeLevelPoints)
                   .filter(Objects::nonNull)
                   .map(BigDecimal::new)
                   .map(bigDecimal -> bigDecimal.setScale(4, RoundingMode.HALF_UP))
                   .findFirst()
                   .orElseThrow();
    }

    private static boolean hasInstitutionPoints(ScientificPerson scientificPerson) {
        return nonNull(scientificPerson.getAuthorPointsForAffiliation());
    }

    private static DbInstitutionPoints toDbInstitutionPoints(InstitutionPoints institutionPoints) {
        return new DbInstitutionPoints(institutionPoints.institutionId(), institutionPoints.points(),
                                       toCreatorPoints(institutionPoints));
    }

    private static List<DbCreatorAffiliationPoints> toCreatorPoints(InstitutionPoints institutionPoints) {
        return institutionPoints.creatorPoints()
                   .stream()
                   .map(CristinMapper::getDbCreatorAffiliationPoints)
                   .collect(Collectors.toList());
    }

    private static CreatorPoints toCreatorPoints(ScientificPerson person) {
        return new CreatorPoints(constructPersonCristinId(person), constructCristinOrganizationId(person),
                                 extractAuthorPointsForAffiliation(person));
    }

    private static DbCreatorAffiliationPoints getDbCreatorAffiliationPoints(CreatorPoints creatorPoints) {
        return new DbCreatorAffiliationPoints(creatorPoints.creatorId(), creatorPoints.affiliationId(),
                                              creatorPoints.points());
    }

    private static BigDecimal extractAuthorPointsForAffiliation(ScientificPerson scientificPerson) {
        return new BigDecimal(scientificPerson.getAuthorPointsForAffiliation());
    }

    private static boolean hasSameInstitutionIdentifier(ScientificPerson scientificPerson,
                                                        CristinLocale cristinLocale) {
        return nonNull(cristinLocale.getInstitutionIdentifier())
               && cristinLocale.getInstitutionIdentifier().equals(scientificPerson.getInstitutionIdentifier());
    }

    private static boolean hasMatchingInstitution(List<CristinLocale> approvedInstitutions,
                                                  CristinDepartmentTransfer departmentTransfer) {
        return approvedInstitutions.stream()
                   .anyMatch(institution -> isMatchingInstitution(departmentTransfer, institution));
    }

    private static boolean isMatchingInstitution(CristinDepartmentTransfer departmentTransfer,
                                                 CristinLocale institution) {
        return Optional.ofNullable(institution.getInstitutionIdentifier())
                   .map(identifier -> identifier.equals(departmentTransfer.getToInstitutionIdentifier()))
                   .or(() -> Optional.of(isMatchingOwnerCode(institution, departmentTransfer)))
                   .orElse(false);
    }

    private static boolean isMatchingOwnerCode(CristinLocale institution,
                                               CristinDepartmentTransfer departmentTransfer) {
        return KREFTREG.equals(institution.getOwnerCode())
               && FHI_CRISTIN_ORG_NUMBER.equals(departmentTransfer.getToInstitutionIdentifier());
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
        if (nonNull(cristinLocale.getInstitutionIdentifier())) {
            return cristinLocale.getInstitutionIdentifier()
                   + AFFILIATION_DELIMITER
                   + cristinLocale.getDepartmentIdentifier()
                   + AFFILIATION_DELIMITER
                   + cristinLocale.getSubDepartmentIdentifier()
                   + AFFILIATION_DELIMITER
                   + cristinLocale.getGroupIdentifier();
        }
        if (KREFTREG.equals(cristinLocale.getOwnerCode())) {
            return FHI_CRISTIN_IDENTIFIER;
        } else {
            return StringUtils.EMPTY_STRING;
        }
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

    private List<DbInstitutionPoints> calculatePoints(CristinNviReport cristinNviReport) {
        var institutions = cristinNviReport.cristinLocales();
        if (institutions.isEmpty()) {
            return List.of();
        } else {
            return cristinNviReport.getCreators().stream()
                       .filter(CristinMapper::hasInstitutionPoints)
                       .collect(collectToInstitutionOfPoints(institutions))
                       .stream()
                       .map(CristinMapper::toDbInstitutionPoints)
                       .collect(Collectors.toList());
        }
    }

    private Collector<ScientificPerson, ?, List<InstitutionPoints>> collectToInstitutionOfPoints(
        List<CristinLocale> institutions) {
        return Collectors.collectingAndThen(
            Collectors.groupingBy(scientificPerson -> getTopLevelOrganization(scientificPerson, institutions),
                                  Collectors.toList()),
            map -> getInstitutionPointsStream(institutions, map).collect(Collectors.toList()));
    }

    private Stream<InstitutionPoints> getInstitutionPointsStream(List<CristinLocale> institutions,
                                                                 Map<URI, List<ScientificPerson>> map) {
        return map.values().stream().map(scientificPeople -> toPoints(institutions, scientificPeople));
    }

    private InstitutionPoints toPoints(List<CristinLocale> institutions, List<ScientificPerson> list) {
        return new InstitutionPoints(
            getTopLevelOrganization(list.getFirst(), institutions),
            list.stream()
                .map(CristinMapper::extractAuthorPointsForAffiliation)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            list.stream().map(CristinMapper::toCreatorPoints).collect(Collectors.toList()));
    }

    private URI getTopLevelOrganization(ScientificPerson scientificPerson, List<CristinLocale> institutions) {
        return institutions.stream()
                   .filter(cristinLocale -> hasSameInstitutionIdentifier(scientificPerson, cristinLocale))
                   .map(CristinMapper::constructInstitutionId)
                   .findFirst()
                   .orElseGet(
                       () -> extractInstitutionIdentifierForTransferredInstitution(scientificPerson, institutions));
    }

    private URI extractInstitutionIdentifierForTransferredInstitution(ScientificPerson scientificPerson,
                                                                      List<CristinLocale> institutions) {
        var matchingTransferInstitutionIdentifier = getMatchingTransfer(scientificPerson, institutions);
        return institutions.stream()
                   .filter(institution -> nonNull(institution.getInstitutionIdentifier())
                                              ? institution.getInstitutionIdentifier()
                                                    .equals(matchingTransferInstitutionIdentifier)
                                              : KREFTREG.equals(institution.getOwnerCode()))
                   .findFirst()
                   .map(CristinMapper::constructInstitutionId)
                   .orElseThrow();
    }

    private String getMatchingTransfer(ScientificPerson scientificPerson, List<CristinLocale> approvalsInstitutions) {
        return departmentTransfers.stream()
                   .filter(departmentTransfer -> scientificPerson.getInstitutionIdentifier()
                                                     .equals(departmentTransfer.getFromInstitutionIdentifier()))
                   .filter(departmentTransfer -> hasMatchingInstitution(approvalsInstitutions, departmentTransfer))
                   .findAny()
                   .map(CristinDepartmentTransfer::getToInstitutionIdentifier)
                   .orElseThrow(
                       () -> new RuntimeException(String.format("%s%s", "No transfer for creator from organizations: ",
                                                                scientificPerson.getInstitutionIdentifier())));
    }
}
