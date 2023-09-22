package no.sikt.nva.nvi.test;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import nva.commons.core.paths.UriWrapper;

public final class TestUtils {

    public static final int SCALE = 10;
    public static final BigDecimal MIN_BIG_DECIMAL = BigDecimal.ZERO;
    public static final BigDecimal MAX_BIG_DECIMAL = BigDecimal.TEN;
    private static final String BUCKET_HOST = "example.org";
    private static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
    private static final String PUBLICATION_API_PATH = "publication";
    private static final String API_HOST = "example.com";

    private TestUtils() {
    }

    public static DbPublicationDate randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new DbPublicationDate(String.valueOf(randomDate.getYear()),
                                     String.valueOf(randomDate.getMonthValue()),
                                     String.valueOf(randomDate.getDayOfMonth()));
    }

    public static URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
    }

    public static URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION_API_PATH)
                   .addChild(identifier.toString())
                   .getUri();
    }

    public static LocalDate randomLocalDate() {
        var daysBetween = ChronoUnit.DAYS.between(START_DATE, LocalDate.now());
        var randomDays = new Random().nextInt((int) daysBetween);

        return START_DATE.plusDays(randomDays);
    }

    public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(applicable)
                   .instanceType(randomString())
                   .points(List.of(new DbInstitutionPoints(randomUri(), randomBigDecimal())))
                   .level(randomElement(DbLevel.values()))
                   .publicationDate(new DbPublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .creators(List.of(new DbCreator(randomUri(), List.of(randomUri()))));
    }

    public static DbCandidate randomApplicableCandidateBuilder() {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(true)
                   .instanceType(randomString())
                   .points(List.of(new DbInstitutionPoints(randomUri(), randomBigDecimal())))
                   .level(randomElement(DbLevel.values()))
                   .publicationDate(new DbPublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .creators(List.of(new DbCreator(randomUri(), List.of(randomUri()))))
                   .build();
    }

    public static String randomYear() {
        return String.valueOf(randomInteger(3000));
    }

    public static DbCandidate randomCandidate() {
        return randomCandidateBuilder(true)
                   .build();
    }

    public static DbCandidate randomCandidateWithInstitution(URI institutionId) {
        return randomCandidateBuilder(true)
                   .creators(List.of(new DbCreator(randomUri(), List.of(institutionId))))
                   .build();
    }

    public static DbNviPeriod.Builder randomNviPeriodBuilder() {
        return DbNviPeriod.builder()
                   .createdBy(randomUsername())
                   .modifiedBy(randomUsername())
                   .reportingDate(getNowWithMillisecondAccuracy())
                   .publishingYear(randomYear());
    }

    public static DbNviPeriod randomNviPeriod() {
        return randomNviPeriodBuilder()
                   .build();
    }

    public static DbApprovalStatus randomApprovalStatus() {
        return new DbApprovalStatus(randomUri(), UUID.randomUUID(), randomElement(DbStatus.values()), randomUsername(),
                                    randomUsername(),
                                    Instant.EPOCH);
    }

    public static DbUsername randomUsername() {
        return DbUsername.fromString(randomString());
    }

    public static BigDecimal randomBigDecimal() {
        var randomBigDecimal =
            MIN_BIG_DECIMAL.add(BigDecimal.valueOf(Math.random()).multiply(MAX_BIG_DECIMAL.subtract(MIN_BIG_DECIMAL)));
        return randomBigDecimal.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static Instant getNowWithMillisecondAccuracy() {
        var now = Instant.now();
        return Instant.ofEpochMilli(now.toEpochMilli());
    }
}
