package no.sikt.nva.nvi.events.cristin;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.InstanceType;

import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;

public final class CristinMapper {

    public static final String API_HOST = new Environment().readEnv("API_HOST");
    public static final String PERSISTED_RESOURCES_BUCKET = new Environment().readEnv("EXPANDED_RESOURCES_BUCKET");
    public static final String AFFILIATION_DELIMITER = ".";
    public static final String CRISTIN = "cristin";
    public static final String ORGANIZATION = "organization";

    private CristinMapper() {

    }

    public static DbCandidate toDbCandidate(CristinNviReport cristinNviReport) {
        return DbCandidate.builder()
                   .publicationId(constructPublicationId(cristinNviReport.publicationIdentifier()))
                   .publicationBucketUri(constructPublicationBucketUri(cristinNviReport.publicationIdentifier()))
                   .publicationDate(constructPublicationDate(cristinNviReport))
                   .applicable(true)
                   .instanceType(InstanceType.IMPORTED_CANDIDATE)
                   .level(DbLevel.NON_CANDIDATE)
                   .build();
    }

    public static List<DbApprovalStatus> toApprovals(CristinNviReport cristinNviReport) {
        return cristinNviReport.nviReport().stream()
                   .map(CristinMapper::toApproval)
                   .toList();
    }

    private static DbApprovalStatus toApproval(CristinLocale cristinLocale) {
        return DbApprovalStatus.builder()
                   .status(DbStatus.APPROVED)
                   .institutionId(constructInstitutionId(cristinLocale))
                   .finalizedDate(constructFinalizedDate(cristinLocale.getDateControlled()))
                   .build();
    }

    private static Instant constructFinalizedDate(LocalDate dateControlled) {
        return dateControlled.atStartOfDay().toInstant(zoneOffset());
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


    private static DbPublicationDate constructPublicationDate(CristinNviReport cristinNviReport) {
        var zonedDateTime = getZonedDateTime(cristinNviReport);
        return DbPublicationDate.builder()
                   .day(String.valueOf(zonedDateTime.getDayOfMonth()))
                   .month(String.valueOf(zonedDateTime.getMonth()))
                   .year(String.valueOf(zonedDateTime.getYear()))
                   .build();
    }

    private static ZonedDateTime getZonedDateTime(CristinNviReport cristinNviReport) {
        return cristinNviReport.publicationDate().atZone(zoneOffset());
    }

    private static URI constructPublicationBucketUri(String publicationIdentifier) {
        return UriWrapper.fromHost(PERSISTED_RESOURCES_BUCKET)
                   .addChild("resources")
                   .addChild(publicationIdentifierKeyInS3Bucket(publicationIdentifier))
                   .getUri();
    }

    private static String publicationIdentifierKeyInS3Bucket(String publicationIdentifier) {
        return publicationIdentifier  + ".gz";
    }

    private static URI constructPublicationId(String publicationIdentifier) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild("publication")
                   .addChild(publicationIdentifier)
                   .getUri();
    }
}
