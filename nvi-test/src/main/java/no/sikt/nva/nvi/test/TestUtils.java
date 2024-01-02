package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.db.model.InstanceType.NON_CANDIDATE;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.UpdateNonCandidateRequest;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class TestUtils {

    public static final int SCALE = 10;
    public static final BigDecimal MIN_BIG_DECIMAL = BigDecimal.ZERO;
    public static final BigDecimal MAX_BIG_DECIMAL = BigDecimal.TEN;
    public static final int CURRENT_YEAR = Year.now().getValue();
    public static final Random RANDOM = new Random();
    public static final String UUID_SEPERATOR = "-";
    private static final String BUCKET_HOST = "example.org";
    private static final LocalDate START_DATE = LocalDate.of(1970, 1, 1);
    private static final String PUBLICATION_API_PATH = "publication";
    private static final String API_HOST = "example.com";

    private TestUtils() {
    }

    public static int randomIntBetween(int min, int max) {
        return RANDOM.nextInt(min, max);
    }

    public static URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost(BUCKET_HOST).addChild(identifier.toString()).getUri();
    }

    public static URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST).addChild(PUBLICATION_API_PATH).addChild(identifier.toString()).getUri();
    }

    public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(applicable)
                   .instanceType(randomInstanceTypeExcluding(NON_CANDIDATE))
                   .points(List.of(new DbInstitutionPoints(randomUri(), randomBigDecimal())))
                   .level(randomElement(DbLevel.values()))
                   .publicationDate(new DbPublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .creators(List.of(new DbCreator(randomUri(), List.of(randomUri()))));
    }

    public static InstanceType randomInstanceType() {
        var instanceTypes = Arrays.stream(InstanceType.values()).toList();
        return instanceTypes.get(RANDOM.nextInt(instanceTypes.size()));
    }

    public static InstanceType randomInstanceTypeExcluding(InstanceType instanceType) {
        var instanceTypes = Arrays.stream(InstanceType.values()).filter(type -> !type.equals(instanceType)).toList();
        return instanceTypes.get(RANDOM.nextInt(instanceTypes.size()));
    }

    public static DbLevel randomLevelExcluding(DbLevel level) {
        var levels = Arrays.stream(DbLevel.values()).filter(type -> !type.equals(level)).toList();
        return levels.get(RANDOM.nextInt(levels.size()));
    }

    public static String randomYear() {
        return String.valueOf(randomIntBetween(START_DATE.getYear(), LocalDate.now().getYear()));
    }

    public static DbCandidate randomCandidate() {
        return randomCandidateBuilder(true).build();
    }

    public static DbApprovalStatus randomApproval() {
        return new DbApprovalStatus(randomUri(),
                                    randomElement(DbStatus.values()),
                                    randomUsername(),
                                    randomUsername(),
                                    randomInstant(),
                                    randomString());
    }

    public static List<CandidateDao> createNumberOfCandidatesForYear(String year, int number,
                                                                     CandidateRepository repository) {
        return IntStream.range(0, number)
                   .mapToObj(i -> randomCandidateWithYear(year))
                   .map(candidate -> repository.create(candidate, List.of()))
                   .toList();
    }

    public static DbNviPeriod.Builder randomNviPeriodBuilder() {
        return DbNviPeriod.builder()
                   .createdBy(randomUsername())
                   .modifiedBy(randomUsername())
                   .reportingDate(getNowWithMillisecondAccuracy())
                   .publishingYear(randomYear());
    }

    public static List<CandidateDao> sortByIdentifier(List<CandidateDao> candidates, Integer limit) {
        var comparator = Comparator.comparing(TestUtils::getCharacterValues);
        return candidates.stream()
                   .sorted(Comparator.comparing(CandidateDao::identifier, comparator))
                   .limit(nonNull(limit) ? limit : candidates.size())
                   .toList();
    }

    public static Map<String, String> getYearIndexStartMarker(CandidateDao dao) {
        return Map.of("PrimaryKeyRangeKey", dao.primaryKeyRangeKey(),
                      "PrimaryKeyHashKey", dao.primaryKeyHashKey(),
                      "SearchByYearHashKey", String.valueOf(dao.searchByYearHashKey()),
                      "SearchByYearRangeKey", String.valueOf(dao.searchByYearSortKey()));
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

    public static DbNviPeriod createPeriod(String publishingYear) {
        return DbNviPeriod.builder()
                   .startDate(ZonedDateTime.now().plusMonths(1).toInstant())
                   .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                   .publishingYear(publishingYear)
                   .createdBy(randomUsername())
                   .build();
    }

    public static UpdateNonCandidateRequest createUpsertNonCandidateRequest(URI publicationId) {
        return () -> publicationId;
    }

    public static UpdateStatusRequest createUpdateStatusRequest(ApprovalStatus status, URI institutionId,
                                                                String username) {
        return UpdateStatusRequest.builder()
                   .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
                   .withApprovalStatus(status)
                   .withInstitutionId(institutionId)
                   .withUsername(username)
                   .build();
    }

    public static UpsertCandidateRequest createUpsertCandidateRequestWithLevel(String level, URI... institutions) {
        return createUpsertCandidateRequest(randomUri(), randomUri(), true, randomInstanceTypeExcluding(NON_CANDIDATE),
                                            1, randomBigDecimal(), level, CURRENT_YEAR, institutions);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(int year) {
        return createUpsertCandidateRequest(randomUri(), randomUri(), true, randomInstanceTypeExcluding(NON_CANDIDATE),
                                            1, randomBigDecimal(),
                                            randomLevelExcluding(DbLevel.NON_CANDIDATE).getVersionOneValue(),
                                            year,
                                            randomUri());
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI... institutions) {
        return createUpsertCandidateRequest(randomUri(), randomUri(), true, randomInstanceTypeExcluding(NON_CANDIDATE),
                                            1, randomBigDecimal(),
                                            randomLevelExcluding(DbLevel.NON_CANDIDATE).getVersionOneValue(),
                                            CURRENT_YEAR,
                                            institutions);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI publicationId,
                                                                      URI publicationBucketUri,
                                                                      boolean isApplicable,
                                                                      InstanceType instanceType,
                                                                      int creatorCount,
                                                                      BigDecimal totalPoints,
                                                                      String level, int year,
                                                                      URI... institutions) {
        var creators = IntStream.of(creatorCount)
                           .mapToObj(i -> randomUri())
                           .collect(Collectors.toMap(Function.identity(), e -> List.of(institutions)));

        var points = Arrays.stream(institutions)
                         .collect(Collectors.toMap(Function.identity(), e -> randomBigDecimal()));

        return createUpsertCandidateRequest(publicationId, publicationBucketUri, isApplicable,
                                            new PublicationDate(String.valueOf(year), null, null), creators,
                                            instanceType,
                                            randomElement(ChannelType.values()).getValue(), randomUri(),
                                            level, points,
                                            randomInteger(), randomBoolean(),
                                            randomBigDecimal(), randomBigDecimal(), totalPoints);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI publicationId,
                                                                      final URI publicationBucketUri,
                                                                      boolean isApplicable,
                                                                      final PublicationDate publicationDate,
                                                                      Map<URI, List<URI>> creators,
                                                                      InstanceType instanceType,
                                                                      String channelType, URI channelId,
                                                                      String level,
                                                                      Map<URI, BigDecimal> points,
                                                                      final Integer creatorShareCount,
                                                                      final boolean isInternationalCollaboration,
                                                                      final BigDecimal collaborationFactor,
                                                                      final BigDecimal basePoints,
                                                                      final BigDecimal totalPoints) {

        return new UpsertCandidateRequest() {

            @Override
            public URI publicationBucketUri() {
                return publicationBucketUri;
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
            public boolean isInternationalCollaboration() {
                return isInternationalCollaboration;
            }

            @Override
            public Map<URI, List<URI>> creators() {
                return creators;
            }

            @Override
            public String channelType() {
                return channelType;
            }

            @Override
            public URI publicationChannelId() {
                return channelId;
            }

            @Override
            public String level() {
                return level;
            }

            @Override
            public String instanceType() {
                return instanceType.getValue();
            }

            @Override
            public PublicationDate publicationDate() {
                return publicationDate;
            }

            @Override
            public int creatorShareCount() {
                return creatorShareCount;
            }

            @Override
            public BigDecimal collaborationFactor() {
                return collaborationFactor;
            }

            @Override
            public BigDecimal basePoints() {
                return basePoints;
            }

            @Override
            public Map<URI, BigDecimal> institutionPoints() {
                return points;
            }

            @Override
            public BigDecimal totalPoints() {
                return totalPoints;
            }
        };
    }

    public static CreateNoteRequest createNoteRequest(String text, String username) {
        return new CreateNoteRequest(text, username);
    }

    private static DbCandidate randomCandidateWithYear(String year) {
        return randomCandidateBuilder(true).publicationDate(publicationDate(year)).build();
    }

    private static DbPublicationDate publicationDate(String year) {
        return new DbPublicationDate(year, null, null);
    }

    private static String getCharacterValues(UUID uuid) {
        return uuid.toString().replaceAll(UUID_SEPERATOR, "");
    }

    private static Instant getNowWithMillisecondAccuracy() {
        var now = Instant.now();
        return Instant.ofEpochMilli(now.toEpochMilli());
    }
}
