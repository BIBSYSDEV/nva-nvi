package no.sikt.nva.nvi.events.cristin;

import static java.util.Objects.nonNull;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
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

    private CristinMapper() {

    }

    //TODO: Extract creators from dbh_forskres_kontroll, remove Jacoco annotation when implemented
    public static DbCandidate toDbCandidate(CristinNviReport cristinNviReport) {
        var now = Instant.now();
        return DbCandidate.builder()
                   .publicationId(constructPublicationId(cristinNviReport.publicationIdentifier()))
                   .publicationBucketUri(constructPublicationBucketUri(cristinNviReport.publicationIdentifier()))
                   .publicationDate(constructPublicationDate(cristinNviReport.publicationDate()))
                   .instanceType(cristinNviReport.instanceType())
                   //                   .creators(extractCreators(cristinNviReport))
                   .level(extractLevel(cristinNviReport))
                   .reportStatus(ReportStatus.REPORTED)
                   .applicable(true)
                   .createdDate(now)
                   .modifiedDate(now)
                   .points(calculatePoints(cristinNviReport))
                   .build();
    }

    @JacocoGenerated
    public static List<DbCreator> extractCreators(CristinNviReport cristinNviReport) {
        return getCreators(cristinNviReport).stream()
                   .collect(groupByCristinIdentifierAndMapToAffiliationId())
                   .entrySet()
                   .stream()
                   .map(CristinMapper::toDbCreator)
                   .toList();
    }

    public static List<DbApprovalStatus> toApprovals(CristinNviReport cristinNviReport) {
        return cristinNviReport.cristinLocales().stream().map(CristinMapper::toApproval).toList();
    }

    private static List<DbInstitutionPoints> calculatePoints(CristinNviReport cristinNviReport) {
        var institutions = cristinNviReport.cristinLocales();
        Map<URI, BigDecimal> collect = getCreators(cristinNviReport).stream()
                                           .collect(collectToMapOfPoints(institutions));
        return collect
                   .entrySet()
                   .stream()
                   .map(CristinMapper::toDbInstitutionPoints)
                   .collect(Collectors.toList());
    }

    private static DbInstitutionPoints toDbInstitutionPoints(Entry<URI, BigDecimal> entry) {
        return new DbInstitutionPoints(entry.getKey(), entry.getValue());
    }

    private static List<ScientificPerson> getCreators(CristinNviReport cristinNviReport) {
        return cristinNviReport.scientificResources().get(0).getCreators();
    }

    private static Collector<ScientificPerson, ?, Map<URI, BigDecimal>> collectToMapOfPoints(
        List<CristinLocale> institutions) {
        return Collectors.groupingBy(scientificPerson -> getTopLevelOrganization(scientificPerson, institutions),
                                     Collectors.reducing(BigDecimal.ZERO, CristinMapper::toBigDecimal,
                                                         BigDecimal::add));
    }

    private static BigDecimal toBigDecimal(ScientificPerson scientificPerson) {
        return new BigDecimal(scientificPerson.getAuthorPointsForAffiliation());
    }

    private static URI getTopLevelOrganization(ScientificPerson scientificPerson, List<CristinLocale> institutions) {
        return institutions.stream()
                   .filter(cristinLocale -> cristinLocale.getInstitutionIdentifier()
                                                .equals(scientificPerson.getInstitutionIdentifier()))
                   .map(CristinMapper::constructInstitutionId)
                   .collect(Collectors.toList())
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
                   .map(DbLevel::parse)
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
