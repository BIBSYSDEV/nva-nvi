package no.sikt.nva.nvi.test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.Note;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.Username;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import nva.commons.core.paths.UriWrapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

public final class TestUtils {

    private static final String BUCKET_HOST = "example.org";
    private static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
    private static final String PUBLICATION_API_PATH = "publication";
    private static final String API_HOST = "example.com";

    private TestUtils() {
    }

    public static ApprovalStatus createPendingApprovalStatus(URI institutionUri) {
        return new ApprovalStatus.Builder()
                   .withStatus(Status.PENDING)
                   .withInstitutionId(institutionUri)
                   .build();
    }

    public static CandidateDetails.PublicationDate randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new CandidateDetails.PublicationDate(String.valueOf(randomDate.getYear()),
                                                    String.valueOf(randomDate.getMonthValue()),
                                                    String.valueOf(randomDate.getDayOfMonth()));
    }

    public static PublicationDate toPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(),
                                   publicationDate.month(),
                                   publicationDate.day());
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

    public static List<Creator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .map(creator -> new Creator(creator.id(), creator.nviInstitutions()))
                   .toList();
    }

    public static Stream<URI> extractNviInstitutionIds(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .flatMap(creatorDto -> creatorDto.nviInstitutions().stream())
                   .distinct();
    }

    public static LocalDate randomLocalDate() {
        var daysBetween = ChronoUnit.DAYS.between(START_DATE, LocalDate.now());
        var randomDays = new Random().nextInt((int) daysBetween);

        return START_DATE.plusDays(randomDays);
    }


    public static Candidate.Builder randomCandidateBuilder() {
        return new Candidate.Builder()
                   .withPublicationId(randomUri())
                   .withPublicationBucketUri(randomUri())
                   .withIsApplicable(randomBoolean())
                   .withInstanceType(randomString())
                   .withLevel(randomElement(Level.values()))
                   .withPublicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .withIsInternationalCollaboration(randomBoolean())
                   .withCreatorCount(randomInteger())
                   .withCreators(List.of(new Creator(randomUri(), List.of(randomUri()))))
                   .withApprovalStatuses(List.of(randomApprovalStatus()))
                   .withNotes(List.of(new Note(randomUsername(), randomString(), getNowWithMillisecondAccuracy())));
    }

    public static String randomYear() {
        return String.valueOf(randomInteger(3000));
    }

    private static Instant getNowWithMillisecondAccuracy() {
        var now = Instant.now();
        return Instant.ofEpochMilli(now.toEpochMilli());
    }

    public static Candidate randomCandidate() {
        return randomCandidateBuilder()
                   .build();
    }

    public static NviPeriod.Builder randomNviPeriodBuilder() {
        return new NviPeriod.Builder()
                   .withCreatedBy(randomUsername())
                   .withModifiedBy(randomUsername())
                   .withReportingDate(getNowWithMillisecondAccuracy())
                   .withPublishingYear(randomYear());

    }

    public static NviPeriod randomNviPeriod() {
        return randomNviPeriodBuilder()
                   .build();
    }

    public static ApprovalStatus randomApprovalStatus() {
        return new ApprovalStatus(randomUri(), randomElement(Status.values()), randomUsername(), Instant.EPOCH);
    }

    public static Username randomUsername() {
        return new Username(randomString());
    }
}
