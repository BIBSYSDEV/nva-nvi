package no.sikt.nva.nvi.events.cristin;

import static java.util.Objects.nonNull;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import nva.commons.core.Environment;
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

    private CristinMapper() {

    }

    public static DbCandidate toDbCandidate(CristinNviReport cristinNviReport) {
        return DbCandidate.builder()
                   .publicationId(constructPublicationId(cristinNviReport.publicationIdentifier()))
                   .publicationBucketUri(constructPublicationBucketUri(cristinNviReport.publicationIdentifier()))
                   .publicationDate(constructPublicationDate(cristinNviReport.publicationDate()))
                   .applicable(true)
                   .level(extractLevel(cristinNviReport))
                   .build();
    }

    private static DbLevel extractLevel(CristinNviReport cristinNviReport) {
        return Optional.ofNullable(cristinNviReport.scientificResources())
                   .map(list -> list.get(0))
                   .map(ScientificResource::getQualityCode)
                   .map(DbLevel::parse)
                   .orElse(null);
    }

    public static List<DbApprovalStatus> toApprovals(CristinNviReport cristinNviReport) {
        return cristinNviReport.cristinLocales().stream()
                   .map(CristinMapper::toApproval)
                   .toList();
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
        var userIdentifier = extractAssigneIdentifier(cristinLocale);
        return nonNull(userIdentifier)
                   ? Username.fromString(constructUsername(cristinLocale, userIdentifier))
                   : null;
    }

    private static String constructUsername(CristinLocale cristinLocale, String userIdentifier) {
        return String.format("%s@%s", userIdentifier, constructInstitutionIdentifier(cristinLocale));
    }

    private static String extractAssigneIdentifier(CristinLocale cristinLocale) {
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
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION)
                   .addChild(publicationIdentifier)
                   .getUri();
    }
}
