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
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
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

    public static final int CURRENT_YEAR = Year.now().getValue();

    private TestUtils() {
    }

    public static DbPublicationDate randomPublicationDate() {
        var randomDate = randomLocalDate();
        return new DbPublicationDate(String.valueOf(randomDate.getYear()), String.valueOf(randomDate.getMonthValue()),
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

    public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(applicable)
                   .instanceType(randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE))
                   .points(List.of(new DbInstitutionPoints(randomUri(), randomBigDecimal())))
                   .level(randomElement(DbLevel.values()))
                   .publicationDate(new DbPublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .creators(List.of(new DbCreator(randomUri(), List.of(randomUri()))));
    }

    public static InstanceType randomInstanceType() {
        var instanceTypes = Arrays.stream(InstanceType.values()).toList();
        return instanceTypes.get(new Random().nextInt(instanceTypes.size()));
    }

    public static InstanceType randomInstanceTypeExcluding(InstanceType instanceType) {
        var instanceTypes = Arrays.stream(InstanceType.values()).filter(type -> !type.equals(instanceType)).toList();
        return instanceTypes.get(new Random().nextInt(instanceTypes.size()));
    }

    public static String randomYear() {
        return String.valueOf(randomInteger(3000));
    }

    public static DbCandidate randomCandidate() {
        return randomCandidateBuilder(true).build();
    }

    public static DbCandidate randomCandidateWithPublicationYear(int year) {
        return randomCandidateBuilder(true)
                   .publicationDate(DbPublicationDate.builder().year(String.valueOf(year)).build()).build();
    }

    public static DbNviPeriod.Builder randomNviPeriodBuilder() {
        return DbNviPeriod.builder()
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
        var nviPeriodRepository = mock(PeriodRepository.class);
        var nviService = new NviService(nviPeriodRepository, new CandidateRepository(client));
        var period = DbNviPeriod.builder()
                         .publishingYear(String.valueOf(year))
                         .startDate(Instant.now())
                         .reportingDate(Instant.now().plusSeconds(300))
                         .build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviService;
    }

    public static NviService nviServiceReturningClosedPeriod(DynamoDbClient client, int year) {
        var nviPeriodRepository = mock(PeriodRepository.class);
        var nviService = new NviService(nviPeriodRepository, new CandidateRepository(client));
        var period = DbNviPeriod.builder().publishingYear(String.valueOf(year)).startDate(Instant.now())
                .reportingDate(Instant.now()).build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviService;
    }

    public static PeriodRepository periodRepositoryReturningClosedPeriod(int year) {
        var nviPeriodRepository = mock(PeriodRepository.class);
        var period = DbNviPeriod.builder().publishingYear(String.valueOf(year))
                         .startDate(ZonedDateTime.now().minusMonths(10).toInstant())
                         .reportingDate(ZonedDateTime.now().minusMonths(1).toInstant()).build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviPeriodRepository;
    }

    public static PeriodRepository periodRepositoryReturningNotOpenedPeriod(int year) {
        var nviPeriodRepository = mock(PeriodRepository.class);
        var period = DbNviPeriod.builder()
                         .publishingYear(String.valueOf(year))
                         .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                         .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant()).build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviPeriodRepository;
    }

    public static PeriodRepository periodRepositoryReturningOpenedPeriod(int year) {
        var nviPeriodRepository = mock(PeriodRepository.class);
        var period = DbNviPeriod.builder()
                         .publishingYear(String.valueOf(year))
                         .startDate(Instant.now())
                         .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant()).build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviPeriodRepository;
    }

    public static NviService nviServiceReturningNotStartedPeriod(DynamoDbClient client, int year) {
        var nviPeriodRepository = mock(PeriodRepository.class);
        var nviService = new NviService(nviPeriodRepository, new CandidateRepository(client));
        var period = DbNviPeriod.builder()
                         .publishingYear(String.valueOf(year))
                         .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                         .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                         .build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviService;
    }

    public static UpdateStatusRequest createUpdateStatusRequest(DbStatus status, URI institutionId, String username) {
        return UpdateStatusRequest.builder()
                   .withReason(DbStatus.REJECTED.equals(status) ? randomString() : null)
                   .withApprovalStatus(status)
                   .withInstitutionId(institutionId)
                   .withUsername(username)
                   .build();
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI... institutions) {
        return createUpsertCandidateRequest(randomUri(), true, 1, InstanceType.ACADEMIC_MONOGRAPH,
                                            institutions);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI publicationId,
                                                                      boolean isApplicable, int creatorCount,
                                                                      final InstanceType instanceType,
                                                                      URI... institutions) {
        var creators = IntStream.of(creatorCount)
                           .mapToObj(i -> randomUri())
                           .collect(Collectors.toMap(Function.identity(), e -> List.of(institutions)));

        var points = Arrays.stream(institutions)
                         .collect(Collectors.toMap(Function.identity(), e -> randomBigDecimal()));

        return createUpsertCandidateRequest(publicationId, isApplicable, creators, instanceType,
                                            DbLevel.LEVEL_TWO, points);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI publicationId,
                                                                      boolean isApplicable,
                                                                      Map<URI, List<URI>> creators,
                                                                      final InstanceType instanceType,
                                                                      DbLevel level,
                                                                      Map<URI, BigDecimal> points) {


        return new UpsertCandidateRequest() {

            @Override
            public URI publicationBucketUri() {
                return randomUri();
            }

            @Override
            public URI publicationId() {
                return publicationId;
            }

            @Override
            public boolean isApplicable() {
                return isApplicable;
            }

            @Override
            public boolean isInternationalCooperation() {
                return false;
            }

            @Override
            public Map<URI, List<URI>> creators() {
                return creators;
            }

            @Override
            public String level() {
                return level.getValue();
            }

            @Override
            public String instanceType() {
                return instanceType.getValue();
            }

            @Override
            public PublicationDate publicationDate() {
                return new PublicationDate(String.valueOf(ZonedDateTime.now().getYear()), null, null);
            }

            @Override
            public Map<URI, BigDecimal> points() {
                return points;
            }

            @Override
            public int creatorCount() {
                return (int) creators.values().stream().mapToLong(List::size).sum();
            }
        };
    }

    public static CreateNoteRequest createNoteRequest(String text, String username) {
        return new CreateNoteRequest(text, username);
    }

    private static Instant getNowWithMillisecondAccuracy() {
        var now = Instant.now();
        return Instant.ofEpochMilli(now.toEpochMilli());
    }
}
