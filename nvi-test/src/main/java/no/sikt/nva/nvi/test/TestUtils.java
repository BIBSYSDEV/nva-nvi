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
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints.DbCreatorAffiliationPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.utils.BatchScanUtil;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpdateNonCandidateRequest;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class TestUtils {

    public static final int SCALE = 4;
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

    public static DbCandidate.Builder randomCandidateBuilder(boolean applicable, URI institutionId) {
        var creatorId = randomUri();
        var institutionPoints = randomBigDecimal();
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .publicationBucketUri(randomUri())
                   .applicable(applicable)
                   .instanceType(randomInstanceTypeExcluding(NON_CANDIDATE.getValue()))
                   .points(List.of(new DbInstitutionPoints(institutionId, institutionPoints,
                                                           List.of(new DbCreatorAffiliationPoints(
                                                               creatorId, institutionId, institutionPoints)))))
                   .level(DbLevel.LEVEL_ONE)
                   .channelType(randomElement(ChannelType.values()))
                   .channelId(randomUri())
                   .publicationDate(new DbPublicationDate(randomString(), randomString(), randomString()))
                   .internationalCollaboration(randomBoolean())
                   .creatorCount(randomInteger())
                   .createdDate(Instant.now())
                   .modifiedDate(Instant.now())
                   .creators(List.of(new DbCreator(creatorId, List.of(institutionId))));
    }

    public static DbCandidate.Builder randomCandidateBuilder(boolean applicable) {
        return randomCandidateBuilder(applicable, randomUri());
    }

    public static InstanceType randomInstanceType() {
        var instanceTypes = Arrays.stream(InstanceType.values()).toList();
        return instanceTypes.get(RANDOM.nextInt(instanceTypes.size()));
    }

    public static no.sikt.nva.nvi.common.service.model.Username randomUserName() {
        return no.sikt.nva.nvi.common.service.model.Username.fromString(randomString());
    }

    public static String randomInstanceTypeExcluding(String instanceType) {
        var instanceTypes = Arrays.stream(InstanceType.values())
                                .filter(type -> !type.getValue().equals(instanceType)).toList();
        return instanceTypes.get(RANDOM.nextInt(instanceTypes.size())).getValue();
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

    public static DbApprovalStatus randomApproval(URI institutionId) {
        return new DbApprovalStatus(institutionId,
                                    randomElement(DbStatus.values()),
                                    randomUsername(),
                                    randomUsername(),
                                    randomInstant(),
                                    randomString());
    }

    public static DbApprovalStatus randomApproval() {
        return randomApproval(randomUri());
    }

    public static List<CandidateDao> createNumberOfCandidatesForYear(String year, int number,
                                                                     CandidateRepository repository) {
        return IntStream.range(0, number)
                   .mapToObj(i -> randomCandidateWithYear(year))
                   .map(candidate -> createCandidateDao(repository, candidate))
                   .toList();
    }

    public static CandidateDao createCandidateDao(CandidateRepository repository, DbCandidate candidate) {
        return repository.create(candidate, List.of());
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
        return randomBigDecimal(SCALE);
    }

    public static BigDecimal randomBigDecimal(int scale) {
        var randomBigDecimal = MIN_BIG_DECIMAL.add(
            BigDecimal.valueOf(Math.random()).multiply(MAX_BIG_DECIMAL.subtract(MIN_BIG_DECIMAL)));
        return randomBigDecimal.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BatchScanUtil nviServiceReturningOpenPeriod(DynamoDbClient client, int year) {
        var nviPeriodRepository = mock(PeriodRepository.class);
        var nviService = new BatchScanUtil(new CandidateRepository(client));
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
                         .id(randomUri())
                         .startDate(Instant.now())
                         .reportingDate(ZonedDateTime.now().plusMonths(10).toInstant()).build();
        when(nviPeriodRepository.findByPublishingYear(anyString())).thenReturn(Optional.of(period));
        return nviPeriodRepository;
    }

    public static NviPeriod setupPersistedPeriod(String year, PeriodRepository periodRepository) {
        return NviPeriod.create(CreatePeriodRequest.builder()
                                    .withPublishingYear(Integer.parseInt(year))
                                    .withStartDate(ZonedDateTime.now().plusMonths(1).toInstant())
                                    .withReportingDate(ZonedDateTime.now().plusMonths(10).toInstant())
                                    .withCreatedBy(no.sikt.nva.nvi.common.service.model.Username.fromString(randomString()))
                                    .build(), periodRepository);
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
        return createUpsertCandidateRequest(randomUri(), randomUri(), true, randomInstanceTypeExcluding(
                                                NON_CANDIDATE.getValue()),
                                            1, randomBigDecimal(), level, CURRENT_YEAR, institutions);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(int year) {
        return createUpsertCandidateRequest(randomUri(), randomUri(), true, randomInstanceTypeExcluding(
                                                NON_CANDIDATE.getValue()),
                                            1, randomBigDecimal(),
                                            randomLevelExcluding(DbLevel.NON_CANDIDATE).getValue(),
                                            year,
                                            randomUri());
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI... institutions) {
        return createUpsertCandidateRequest(randomUri(), randomUri(), true, randomInstanceTypeExcluding(
                                                NON_CANDIDATE.getValue()),
                                            1, randomBigDecimal(),
                                            randomLevelExcluding(DbLevel.NON_CANDIDATE).getValue(),
                                            CURRENT_YEAR,
                                            institutions);
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI topLevelOrg, URI affiliation) {
        return createUpsertCandidateRequest(topLevelOrg, affiliation, randomElement(ChannelType.values()));
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI topLevelOrg, URI affiliation, ChannelType channelType) {
        var creatorId = randomUri();
        var creators = Map.of(creatorId, List.of(affiliation));
        var points = randomBigDecimal();
        var institutionPoints = List.of(new InstitutionPoints(topLevelOrg, points,
                                                              List.of(new CreatorAffiliationPoints(
                                                                  creatorId, affiliation, points))));

        return createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                            new PublicationDate(String.valueOf(CURRENT_YEAR), null, null), creators,
                                            randomInstanceTypeExcluding(
                                                NON_CANDIDATE.getValue()),
                                            channelType.getValue(), randomUri(),
                                            randomLevelExcluding(DbLevel.NON_CANDIDATE).getValue(), institutionPoints,
                                            randomInteger(), randomBoolean(),
                                            randomBigDecimal(), randomBigDecimal(), randomBigDecimal());
    }

    public static UpsertCandidateRequest createUpsertCandidateRequest(URI publicationId,
                                                                      URI publicationBucketUri,
                                                                      boolean isApplicable,
                                                                      String instanceType,
                                                                      int creatorCount,
                                                                      BigDecimal totalPoints,
                                                                      String level, int year,
                                                                      URI... institutions) {
        var creators = IntStream.of(creatorCount)
                           .mapToObj(i -> randomUri())
                           .collect(Collectors.toMap(Function.identity(), e -> List.of(institutions)));

        var points = Arrays.stream(institutions)
                         .map(institution -> {
                             var institutionPoints = randomBigDecimal();
                             return new InstitutionPoints(institution, institutionPoints,
                                                          creators.keySet().stream()
                                                              .map(creator -> new CreatorAffiliationPoints(
                                                                  creator, institution, institutionPoints))
                                                              .toList());
                         }).toList();

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
                                                                      String instanceType,
                                                                      String channelType, URI channelId,
                                                                      String level,
                                                                      List<InstitutionPoints> points,
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
                return instanceType;
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
            public List<InstitutionPoints> institutionPoints() {
                return points;
            }

            @Override
            public BigDecimal totalPoints() {
                return totalPoints;
            }
        };
    }

    public static CreateNoteRequest createNoteRequest(String text, String username) {
        return new CreateNoteRequest(text, username, randomUri());
    }

    public static DbCandidate randomCandidateWithYear(String year) {
        return randomCandidateBuilder(true).publicationDate(publicationDate(year)).build();
    }

    public static CandidateDao setupReportedCandidate(CandidateRepository repository, String year) {
        var institutionId = randomUri();
        return repository.create(randomCandidateBuilder(true, institutionId)
                                     .publicationDate(DbPublicationDate.builder().year(year).build())
                                     .reportStatus(ReportStatus.REPORTED)
                                     .build(),
                                 List.of(randomApproval(institutionId)));
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
