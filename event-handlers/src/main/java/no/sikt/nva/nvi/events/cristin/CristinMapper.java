package no.sikt.nva.nvi.events.cristin;

import static java.util.Collections.emptyList;
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
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.events.cristin.InstitutionPoints.CreatorPoints;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import nva.commons.core.paths.UriWrapper;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public final class CristinMapper {

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
  private static final String PARENT_PUBLICATION_SERIES_LEVEL_JSON_POINTER =
      ENTITY_DESCRIPTION + "/reference/publicationContext/series" + "/level";
  private static final String PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER =
      "/publicationContext/entityDescription/reference"
          + "/publicationContext/series/scientificValue";
  private static final String PARENT_PUBLICATION_SERIES_ID_JSON_POINTER =
      ENTITY_DESCRIPTION + "/reference/publicationContext/series/id";
  private static final String PARENT_PUBLICATION_PUBLISHER_ID_JSON_POINTER =
      ENTITY_DESCRIPTION + "/reference/publicationContext" + "/publisher/id";
  private static final String PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER =
      ENTITY_DESCRIPTION + "/reference/publicationContext/series" + "/type";
  private static final String PARENT_PUBLICATION_PUBLISHER_TYPE_JSON_POINTER =
      ENTITY_DESCRIPTION + "/reference" + "/publicationContext/publisher/type";
  private static final String PUBLISHER_ID_JSON_POINTER = "/publicationContext/publisher/id";
  private static final String SERIES_TYPE_JSON_POINTER = "/publicationContext/series/type";
  private static final String PUBLISHER_TYPE_JSON_POINTER = "/publicationContext/publisher/type";
  private static final String PUBLICATION_CONTEXT_ID_JSON_POINTER = "/publicationContext/id";
  private static final String PUBLICATION_CONTEXT_TYPE_JSON_POINTER = "/publicationContext/type";
  private static final String KREFTREG = "KREFTREG";
  private static final String FHI_CRISTIN_ORG_NUMBER = "7502";
  private static final String INTERNATIONAL_COLLABORATION_FACTOR = "1.3";
  private final List<CristinDepartmentTransfer> departmentTransfers;
  private final String apiHost;
  private final String expandedResourcesBucket;

  private CristinMapper(List<CristinDepartmentTransfer> transfers, Environment environment) {
    this.departmentTransfers = transfers;
    this.apiHost = environment.readEnv("API_HOST");
    this.expandedResourcesBucket = environment.readEnv("EXPANDED_RESOURCES_BUCKET");
  }

  public static CristinMapper withDepartmentTransfers(
      List<CristinDepartmentTransfer> transfers, Environment environment) {
    return new CristinMapper(transfers, environment);
  }

  public List<DbCreatorType> extractCreators(CristinNviReport cristinNviReport) {
    return cristinNviReport.getCreators().stream()
        .filter(CristinMapper::hasInstitutionPoints)
        .collect(groupByCristinIdentifierAndMapToAffiliationId())
        .entrySet()
        .stream()
        .map(CristinMapper::toDbCreator)
        .toList();
  }

  public DbCandidate toDbCandidate(CristinNviReport cristinNviReport) {
    var channel =
        DbPublicationChannel.builder()
            .id(extractChannelId(cristinNviReport))
            .channelType(extractChannelType(cristinNviReport))
            .scientificValue(cristinNviReport.getLevel().getValue())
            .build();
    var points = calculatePoints(cristinNviReport);
    var pointCalculation =
        new DbPointCalculation(
            extractBasePoints(cristinNviReport),
            extractCollaborationFactor(cristinNviReport),
            sumPoints(points),
            channel,
            points,
            isInternationalCollaboration(cristinNviReport),
            0,
            cristinNviReport.instanceType());
    var publicationDetails = toDbPublication(cristinNviReport);
    return DbCandidate.builder()
        .publicationId(publicationDetails.id())
        .publicationBucketUri(publicationDetails.publicationBucketUri())
        .publicationDate(publicationDetails.publicationDate())
        .pointCalculation(pointCalculation)
        .publicationDetails(publicationDetails)
        .instanceType(cristinNviReport.instanceType())
        .level(cristinNviReport.getLevel())
        .reportStatus(ReportStatus.REPORTED)
        .applicable(true)
        .createdDate(publicationDetails.modifiedDate())
        .modifiedDate(publicationDetails.modifiedDate())
        .points(points)
        .totalPoints(sumPoints(points))
        .basePoints(extractBasePoints(cristinNviReport))
        .collaborationFactor(extractCollaborationFactor(cristinNviReport))
        .internationalCollaboration(isInternationalCollaboration(cristinNviReport))
        .creators(publicationDetails.creators())
        .channelId(extractChannelId(cristinNviReport))
        .channelType(extractChannelType(cristinNviReport))
        .build();
  }

  private DbPublicationDetails toDbPublication(CristinNviReport cristinNviReport) {
    var now = Instant.now();
    var creators = extractCreators(cristinNviReport);
    return DbPublicationDetails.builder()
        .id(constructPublicationId(cristinNviReport))
        .identifier(cristinNviReport.publicationIdentifier())
        .publicationBucketUri(
            constructPublicationBucketUri(cristinNviReport.publicationIdentifier()))
        .publicationDate(cristinNviReport.publicationDate().toDbPublicationDate())
        .modifiedDate(now)
        .contributorCount(creators.size())
        .creators(creators)
        .topLevelNviOrganizations(emptyList())
        .build();
  }

  public List<DbApprovalStatus> toApprovals(CristinNviReport cristinNviReport) {
    return cristinNviReport.cristinLocales().stream()
        .filter(
            cristinLocale ->
                nonNull(cristinLocale.getInstitutionIdentifier()) || isKreftReg(cristinLocale))
        .map(this::toApproval)
        .collect(
            Collectors.toMap(
                DbApprovalStatus::institutionId,
                approval -> approval,
                (existing, replacement) -> existing))
        .values()
        .stream()
        .toList();
  }

  private static Boolean isKreftReg(CristinLocale cristinLocale) {
    return Optional.ofNullable(cristinLocale.getOwnerCode()).map(KREFTREG::equals).orElse(false);
  }

  private static BigDecimal sumPoints(List<DbInstitutionPoints> points) {
    return points.stream()
        .map(DbInstitutionPoints::points)
        .reduce(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), BigDecimal::add);
  }

  private static String extractChannelType(CristinNviReport cristinNviReport) {
    var instance = toInstanceType(cristinNviReport.instanceType());
    var referenceNode = cristinNviReport.reference();
    if (nonNull(instance) && nonNull(referenceNode)) {
      var channelType =
          switch (instance) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                extractJsonNodeTextValue(referenceNode, PUBLICATION_CONTEXT_TYPE_JSON_POINTER);
            case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY ->
                extractChannelTypeForAcademicMonograph(referenceNode);
            case ACADEMIC_CHAPTER -> extractChannelTypeForAcademicChapter(referenceNode);
            case NON_CANDIDATE -> null;
          };
      return Optional.ofNullable(channelType)
          .map(ChannelType::parse)
          .map(ChannelType::getValue)
          .orElse(null);
    }
    return null;
  }

  private static URI extractChannelId(CristinNviReport cristinNviReport) {
    var instance = toInstanceType(cristinNviReport.instanceType());
    var referenceNode = cristinNviReport.reference();
    if (nonNull(instance) && nonNull(referenceNode)) {
      var channelId =
          switch (instance) {
            case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
                extractJsonNodeTextValue(referenceNode, PUBLICATION_CONTEXT_ID_JSON_POINTER);
            case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY ->
                extractChannelIdForAcademicMonograph(referenceNode);
            case ACADEMIC_CHAPTER -> extractChannelIdForAcademicChapter(referenceNode);
            case NON_CANDIDATE -> null;
          };
      return attempt(() -> UriWrapper.fromUri(channelId).getUri()).orElse(failure -> null);
    }
    return null;
  }

  private static InstanceType toInstanceType(String instanceType) {
    return InstanceType.parse(instanceType);
  }

  private static String extractChannelIdForAcademicChapter(JsonNode referenceNode) {
    if (nonNull(
            extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_LEVEL_JSON_POINTER))
        || nonNull(
            extractJsonNodeTextValue(
                referenceNode, PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER))) {
      return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_ID_JSON_POINTER);
    } else {
      return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_PUBLISHER_ID_JSON_POINTER);
    }
  }

  private static String extractChannelTypeForAcademicChapter(JsonNode referenceNode) {
    if (nonNull(
            extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER))
        || nonNull(
            extractJsonNodeTextValue(
                referenceNode, PARENT_PUBLICATION_SERIES_SCIENTIFIC_VALUE_JSON_POINTER))) {
      return extractJsonNodeTextValue(referenceNode, PARENT_PUBLICATION_SERIES_TYPE_JSON_POINTER);
    } else {
      return extractJsonNodeTextValue(
          referenceNode, PARENT_PUBLICATION_PUBLISHER_TYPE_JSON_POINTER);
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
    return cristinNviReport.getCreators().stream()
        .map(ScientificPerson::getCollaborationFactor)
        .filter(Objects::nonNull)
        .findFirst()
        .map(INTERNATIONAL_COLLABORATION_FACTOR::equals)
        .orElse(false);
  }

  private static BigDecimal extractCollaborationFactor(CristinNviReport cristinNviReport) {
    return cristinNviReport.getCreators().stream()
        .map(ScientificPerson::getCollaborationFactor)
        .filter(Objects::nonNull)
        .map(BigDecimal::new)
        .map(bigDecimal -> bigDecimal.setScale(4, RoundingMode.HALF_UP))
        .findFirst()
        .orElse(null);
  }

  private static BigDecimal extractBasePoints(CristinNviReport cristinNviReport) {
    return cristinNviReport.getCreators().stream()
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
    return new DbInstitutionPoints(
        institutionPoints.institutionId(),
        institutionPoints.points(),
        toCreatorPoints(institutionPoints));
  }

  private static List<DbCreatorAffiliationPoints> toCreatorPoints(
      InstitutionPoints institutionPoints) {
    return institutionPoints.creatorPoints().stream()
        .map(CristinMapper::getDbCreatorAffiliationPoints)
        .toList();
  }

  private CreatorPoints toCreatorPoints(ScientificPerson person) {
    return new CreatorPoints(
        constructPersonCristinId(person),
        constructCristinOrganizationId(person),
        extractAuthorPointsForAffiliation(person));
  }

  private static DbCreatorAffiliationPoints getDbCreatorAffiliationPoints(
      CreatorPoints creatorPoints) {
    return new DbCreatorAffiliationPoints(
        creatorPoints.creatorId(), creatorPoints.affiliationId(), creatorPoints.points());
  }

  private static BigDecimal extractAuthorPointsForAffiliation(ScientificPerson scientificPerson) {
    return new BigDecimal(scientificPerson.getAuthorPointsForAffiliation());
  }

  private static boolean hasSameInstitutionIdentifier(
      ScientificPerson scientificPerson, CristinLocale cristinLocale) {
    return nonNull(cristinLocale.getInstitutionIdentifier())
        && cristinLocale
            .getInstitutionIdentifier()
            .equals(scientificPerson.getInstitutionIdentifier());
  }

  private static boolean hasMatchingInstitution(
      List<CristinLocale> approvedInstitutions, CristinDepartmentTransfer departmentTransfer) {
    return approvedInstitutions.stream()
        .anyMatch(institution -> isMatchingInstitution(departmentTransfer, institution));
  }

  private static boolean isMatchingInstitution(
      CristinDepartmentTransfer departmentTransfer, CristinLocale institution) {
    return Optional.ofNullable(institution.getInstitutionIdentifier())
        .map(identifier -> identifier.equals(departmentTransfer.getToInstitutionIdentifier()))
        .or(() -> Optional.of(isMatchingOwnerCode(institution, departmentTransfer)))
        .orElse(false);
  }

  private static boolean isMatchingOwnerCode(
      CristinLocale institution, CristinDepartmentTransfer departmentTransfer) {
    return KREFTREG.equals(institution.getOwnerCode())
        && FHI_CRISTIN_ORG_NUMBER.equals(departmentTransfer.getToInstitutionIdentifier());
  }

  @JacocoGenerated
  private static DbCreatorType toDbCreator(Entry<URI, List<URI>> entry) {
    return DbCreator.builder().creatorId(entry.getKey()).affiliations(entry.getValue()).build();
  }

  @JacocoGenerated
  private Collector<ScientificPerson, ?, Map<URI, List<URI>>>
      groupByCristinIdentifierAndMapToAffiliationId() {
    return Collectors.groupingBy(
        this::constructPersonCristinId,
        Collectors.mapping(this::constructCristinOrganizationId, Collectors.toList()));
  }

  @JacocoGenerated
  private URI constructPersonCristinId(ScientificPerson scientificPerson) {
    return UriWrapper.fromHost(apiHost)
        .addChild(CRISTIN)
        .addChild(PERSON)
        .addChild(scientificPerson.getCristinPersonIdentifier())
        .getUri();
  }

  @JacocoGenerated
  private URI constructCristinOrganizationId(ScientificPerson scientificPerson) {
    return UriWrapper.fromHost(apiHost)
        .addChild(CRISTIN)
        .addChild(ORGANIZATION)
        .addChild(scientificPerson.getOrganization())
        .getUri();
  }

  private DbApprovalStatus toApproval(CristinLocale cristinLocale) {
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
    return nonNull(userIdentifier)
        ? Username.fromString(constructUsername(cristinLocale, userIdentifier))
        : null;
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

  private URI constructInstitutionId(CristinLocale cristinLocale) {
    return UriWrapper.fromHost(apiHost)
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

  private URI constructPublicationBucketUri(String publicationIdentifier) {
    return UriWrapper.fromUri("s3://" + expandedResourcesBucket)
        .addChild(RESOURCES)
        .addChild(withGzExtension(publicationIdentifier))
        .getUri();
  }

  private static String withGzExtension(String publicationIdentifier) {
    return publicationIdentifier + GZ_EXTENSION;
  }

  public URI constructPublicationId(CristinNviReport cristinNviReport) {
    return UriWrapper.fromHost(apiHost)
        .addChild(PUBLICATION)
        .addChild(cristinNviReport.publicationIdentifier())
        .getUri();
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
          .toList();
    }
  }

  private Collector<ScientificPerson, ?, List<InstitutionPoints>> collectToInstitutionOfPoints(
      List<CristinLocale> institutions) {
    return Collectors.collectingAndThen(
        Collectors.groupingBy(
            scientificPerson -> getTopLevelOrganization(scientificPerson, institutions),
            Collectors.toList()),
        map -> getInstitutionPointsStream(institutions, map).toList());
  }

  private Stream<InstitutionPoints> getInstitutionPointsStream(
      List<CristinLocale> institutions, Map<URI, List<ScientificPerson>> map) {
    return map.values().stream().map(scientificPeople -> toPoints(institutions, scientificPeople));
  }

  private InstitutionPoints toPoints(
      List<CristinLocale> institutions, List<ScientificPerson> list) {
    return new InstitutionPoints(
        getTopLevelOrganization(list.getFirst(), institutions),
        list.stream()
            .map(CristinMapper::extractAuthorPointsForAffiliation)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        list.stream().map(this::toCreatorPoints).toList());
  }

  private URI getTopLevelOrganization(
      ScientificPerson scientificPerson, List<CristinLocale> institutions) {
    return institutions.stream()
        .filter(cristinLocale -> hasSameInstitutionIdentifier(scientificPerson, cristinLocale))
        .map(this::constructInstitutionId)
        .findFirst()
        .orElseGet(
            () ->
                extractInstitutionIdentifierForTransferredInstitution(
                    scientificPerson, institutions));
  }

  private URI extractInstitutionIdentifierForTransferredInstitution(
      ScientificPerson scientificPerson, List<CristinLocale> institutions) {
    var matchingTransferInstitutionIdentifier = getMatchingTransfer(scientificPerson, institutions);
    return institutions.stream()
        .filter(
            institution ->
                nonNull(institution.getInstitutionIdentifier())
                    ? institution
                        .getInstitutionIdentifier()
                        .equals(matchingTransferInstitutionIdentifier)
                    : KREFTREG.equals(institution.getOwnerCode()))
        .findFirst()
        .map(this::constructInstitutionId)
        .orElseThrow();
  }

  private String getMatchingTransfer(
      ScientificPerson scientificPerson, List<CristinLocale> approvalsInstitutions) {
    return departmentTransfers.stream()
        .filter(
            departmentTransfer ->
                scientificPerson
                    .getInstitutionIdentifier()
                    .equals(departmentTransfer.getFromInstitutionIdentifier()))
        .filter(
            departmentTransfer -> hasMatchingInstitution(approvalsInstitutions, departmentTransfer))
        .findAny()
        .map(CristinDepartmentTransfer::getToInstitutionIdentifier)
        .orElseThrow(
            () ->
                new RuntimeException(
                    String.format(
                        "%s%s",
                        "No transfer for creator from organizations: ",
                        scientificPerson.getInstitutionIdentifier())));
  }
}
