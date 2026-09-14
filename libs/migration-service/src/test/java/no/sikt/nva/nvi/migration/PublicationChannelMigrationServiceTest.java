package no.sikt.nva.nvi.migration;

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
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicationChannelMigrationServiceTest {

  private TestScenario scenario;
  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private PublicationChannelMigrationService migrationService;
  private SampleExpandedPublicationFactory publicationFactory;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    var storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();
    migrationService = new PublicationChannelMigrationService(candidateService, storageReader);
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
  void shouldKeepPersistedCandidateChannelWhenPublicationHasAnotherChannel() {
    var publication = publicationFactory.getExpandedPublication();
    var replacedChannelId = randomUri();
    var candidateId =
        createLegacyCandidateWithChannel(publication, channelWithoutMetadata(replacedChannelId));

    migrationService.migrateCandidate(candidateId);

    var migratedChannel =
        candidateService.getCandidateByIdentifier(candidateId).getPublicationChannel();
    assertThat(migratedChannel.id()).isEqualTo(replacedChannelId);
    assertThat(migratedChannel.name()).isNull();
    assertThat(migratedChannel.printIssn()).isNull();
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
