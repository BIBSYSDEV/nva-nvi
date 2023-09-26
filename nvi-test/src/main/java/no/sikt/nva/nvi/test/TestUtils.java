package no.sikt.nva.nvi.test;

import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.Creator;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstitutionPoints;
import no.sikt.nva.nvi.common.db.model.CandidateDao.ChannelLevel;
import no.sikt.nva.nvi.common.db.model.CandidateDao.PublicationDate;
import no.sikt.nva.nvi.common.db.model.PeriodDao.PeriodData;
import no.sikt.nva.nvi.common.db.NviPeriodRepository;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

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

    public static PublicationDate randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new PublicationDate(String.valueOf(randomDate.getYear()), String.valueOf(randomDate.getMonthValue()),
                                   String.valueOf(randomDate.getDayOfMonth()));
    }

    public static URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
    }

    public static URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST).addChild(PUBLICATION_API_PATH).addChild(identifier.toString()).getUri();
    }

    public static LocalDate randomLocalDate() {
        var daysBetween = ChronoUnit.DAYS.between(START_DATE, LocalDate.now());
        var randomDays = new Random().nextInt((int) daysBetween);

        return START_DATE.plusDays(randomDays);
    }

    public static CandidateData.Builder randomCandidateBuilder(boolean applicable) {
        return CandidateData.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(applicable)
                   .instanceType(randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE))
                   .points(List.of(new InstitutionPoints(randomUri(), randomBigDecimal())))
                   .level(randomElement(ChannelLevel.values()))
                   .publicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .creators(List.of(new Creator(randomUri(), List.of(randomUri()))));
    }

    public static InstanceType randomInstanceType() {
        var instanceTypes = Arrays.stream(InstanceType.values()).toList();
        return instanceTypes.get(new Random().nextInt(instanceTypes.size()));
    }

    public static InstanceType randomInstanceTypeExcluding(InstanceType instanceType) {
        var instanceTypes = Arrays.stream(InstanceType.values()).filter(type -> !type.equals(instanceType)).toList();
        return instanceTypes.get(new Random().nextInt(instanceTypes.size()));
    }

    public static CandidateData randomApplicableCandidateBuilder() {
        return CandidateData.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(true)
                   .instanceType(randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE))
                   .points(List.of(new InstitutionPoints(randomUri(), randomBigDecimal())))
                   .level(randomElement(ChannelLevel.values()))
                   .publicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .creators(List.of(new Creator(randomUri(), List.of(randomUri()))))
                   .build();
    }

    public static String randomYear() {
        return String.valueOf(randomInteger(3000));
    }

    public static CandidateData randomCandidate() {
        return randomCandidateBuilder(true).build();
    }

    public static CandidateData randomCandidateWithPublicationYear(int year) {
        return randomCandidateBuilder(true)
                   .publicationDate(PublicationDate.builder().year(String.valueOf(year)).build()).build();
    }

    public static PeriodData.Builder randomNviPeriodBuilder() {
        return PeriodData.builder()
                   .createdBy(randomUsername())
                   .modifiedBy(randomUsername())
                   .reportingDate(getNowWithMillisecondAccuracy())
                   .publishingYear(randomYear());
    }

    public static Username randomUsername() {
        return Username.fromString(randomString());
    }

    public static BigDecimal randomBigDecimal() {
        var randomBigDecimal = MIN_BIG_DECIMAL.add(
            BigDecimal.valueOf(Math.random()).multiply(MAX_BIG_DECIMAL.subtract(MIN_BIG_DECIMAL)));
        return randomBigDecimal.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static NviService nviServiceReturningOpenPeriod(DynamoDbClient client, int year) {
        var nviPeriodRepository = mock(NviPeriodRepository.class);
        var nviService = new NviService(client, nviPeriodRepository);
        var period = PeriodData.builder()
                         .publishingYear(String.valueOf(year))
                         .reportingDate(Instant.now().plusSeconds(300))
                         .build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviService;
    }

    public static NviService nviServiceReturningClosedPeriod(DynamoDbClient client, int year) {
        var nviPeriodRepository = mock(NviPeriodRepository.class);
        var nviService = new NviService(client, nviPeriodRepository);
        var period = PeriodData.builder().publishingYear(String.valueOf(year)).reportingDate(Instant.now()).build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviService;
    }

    private static Instant getNowWithMillisecondAccuracy() {
        var now = Instant.now();
        return Instant.ofEpochMilli(now.toEpochMilli());
    }
}
