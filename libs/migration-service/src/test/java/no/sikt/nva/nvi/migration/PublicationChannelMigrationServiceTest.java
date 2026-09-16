package no.sikt.nva.nvi.migration;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.randomDbPublicationChannelBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.JOURNAL_TYPE;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.PublicationChannelRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.uriretriever.FakeUriRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicationChannelMigrationServiceTest {

  private static final String APPLICATION_JSON = "application/json";
  private static final String CHANNEL_NAME_IN_REGISTRY = "Abasyn Journal of Life Sciences (AJLS)";
  private static final String CHANNEL_PRINT_ISSN_IN_REGISTRY = "2616-9754";
  private static final String CHANNEL_RESPONSE_TEMPLATE =
      """
      {
        "id" : "%s",
        "identifier" : "81543BCB-50FD-4277-8457-28E5674BA406",
        "name" : "%s",
        "onlineIssn" : "2663-1040",
        "printIssn" : "%s",
        "scientificValue" : "LevelOne",
        "sameAs" : "https://kanalregister.hkdir.no/publiseringskanaler/info/tidsskrift?pid=81543BCB-50FD-4277-8457-28E5674BA406",
        "type" : "Journal",
        "year" : "2024",
        "@context" : "https://bibsysdev.github.io/src/publication-channel/channel-context.json"
      }
      """;

  private TestScenario scenario;
  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private FakeUriRetriever uriRetriever;
  private PublicationChannelMigrationService migrationService;
  private SampleExpandedPublicationFactory publicationFactory;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    var storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();
    uriRetriever = FakeUriRetriever.newInstance();
    migrationService =
        new PublicationChannelMigrationService(
            candidateService, storageReader, new PublicationChannelRetriever(uriRetriever));
    setupOpenPeriod(scenario, CURRENT_YEAR);
    publicationFactory = new SampleExpandedPublicationFactory();
  }

  @Test
  void shouldMigrateChannelNameAndIssn() {
    var publication = publicationFactory.getExpandedPublication();
    var channelInPublication = publicationFactory.getPublicationChannel(JOURNAL_TYPE);
    var candidateId =
        createLegacyCandidateWithChannel(
            publication, channelWithoutMetadata(channelInPublication.id()));

    migrationService.migrateCandidate(candidateId);

    var migratedChannel =
        candidateService.getCandidateByIdentifier(candidateId).getPublicationChannel();
    assertThat(migratedChannel.name()).isEqualTo(channelInPublication.name());
    assertThat(migratedChannel.printIssn()).isEqualTo(channelInPublication.printIssn());
  }

  @Test
  void shouldNotOverwriteExistingChannelMetadata() {
    var publication = publicationFactory.getExpandedPublication();
    var channelInPublication = publicationFactory.getPublicationChannel(JOURNAL_TYPE);
    var reportedChannel =
        randomDbPublicationChannelBuilder()
            .id(channelInPublication.id())
            .name("Name used for reporting")
            .printIssn("2159-4848")
            .build();
    var candidateId = createLegacyCandidateWithChannel(publication, reportedChannel);

    migrationService.migrateCandidate(candidateId);

    var migratedChannel =
        candidateService.getCandidateByIdentifier(candidateId).getPublicationChannel();
    assertThat(migratedChannel.name()).isEqualTo("Name used for reporting");
    assertThat(migratedChannel.printIssn()).isEqualTo("2159-4848");
  }

  @Test
  void shouldFetchChannelMetadataWhenPublicationHasAnotherChannel() {
    var publication = publicationFactory.getExpandedPublication();
    var reportedChannel = channelWithoutMetadata(randomUri());
    registerChannelResponse(reportedChannel.id());
    var candidateId = createLegacyCandidateWithChannel(publication, reportedChannel);

    migrationService.migrateCandidate(candidateId);

    var migratedChannel =
        candidateService.getCandidateByIdentifier(candidateId).getPublicationChannel();
    assertThat(migratedChannel.id()).isEqualTo(reportedChannel.id());
    assertThat(migratedChannel.name()).isEqualTo(CHANNEL_NAME_IN_REGISTRY);
    assertThat(migratedChannel.printIssn()).isEqualTo(CHANNEL_PRINT_ISSN_IN_REGISTRY);
  }

  @Test
  void shouldKeepScientificValueAndTypeWhenFetchingChannelMetadata() {
    var publication = publicationFactory.getExpandedPublication();
    var reportedChannel = channelWithoutMetadata(randomUri());
    registerChannelResponse(reportedChannel.id());
    var candidateId = createLegacyCandidateWithChannel(publication, reportedChannel);

    migrationService.migrateCandidate(candidateId);

    var migratedChannel =
        candidateService.getCandidateByIdentifier(candidateId).getPublicationChannel();
    assertThat(migratedChannel.scientificValue().getValue())
        .isEqualTo(reportedChannel.scientificValue());
    assertThat(migratedChannel.channelType().getValue()).isEqualTo(reportedChannel.channelType());
  }

  @Test
  void shouldKeepPersistedChannelWhenChannelCannotBeFetched() {
    var publication = publicationFactory.getExpandedPublication();
    var replacedChannelId = randomUri();
    uriRetriever.registerResponse(replacedChannelId, HTTP_NOT_FOUND, APPLICATION_JSON, "");
    var candidateId =
        createLegacyCandidateWithChannel(publication, channelWithoutMetadata(replacedChannelId));

    migrationService.migrateCandidate(candidateId);

    var migratedChannel =
        candidateService.getCandidateByIdentifier(candidateId).getPublicationChannel();
    assertThat(migratedChannel.id()).isEqualTo(replacedChannelId);
    assertThat(migratedChannel.name()).isNull();
    assertThat(migratedChannel.printIssn()).isNull();
  }

  private void registerChannelResponse(URI channelId) {
    var body =
        CHANNEL_RESPONSE_TEMPLATE.formatted(
            channelId, CHANNEL_NAME_IN_REGISTRY, CHANNEL_PRINT_ISSN_IN_REGISTRY);
    uriRetriever.registerResponse(channelId, HTTP_OK, APPLICATION_JSON, body);
  }

  private static DbPublicationChannel channelWithoutMetadata(URI channelId) {
    return randomDbPublicationChannelBuilder().id(channelId).name(null).printIssn(null).build();
  }

  private UUID createLegacyCandidateWithChannel(
      SampleExpandedPublication publication, DbPublicationChannel channel) {
    scenario.setupExpandedPublicationInS3(publication);
    var topLevelInstitution = randomOrganizationId();
    var dbDetails = randomPublicationBuilder(publication.identifier(), topLevelInstitution);
    var dbCandidate =
        randomCandidateBuilder(topLevelInstitution, dbDetails.build())
            .reportStatus(ReportStatus.REPORTED)
            .reportedDate(Instant.now())
            .pointCalculation(
                randomPointCalculationBuilder(randomUri(), randomOrganizationId())
                    .publicationChannel(channel)
                    .build())
            .build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }
}
