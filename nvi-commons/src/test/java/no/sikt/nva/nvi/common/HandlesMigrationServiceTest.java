package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// TODO: Remove service
@Deprecated(forRemoval = true)
class HandlesMigrationServiceTest {

  private TestScenario scenario;
  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private HandlesMigrationService migrationService;
  private SampleExpandedPublicationFactory publicationFactory;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    var storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();
    migrationService = new HandlesMigrationService(candidateService, storageReader);
    setupOpenPeriod(scenario, CURRENT_YEAR);
    publicationFactory = new SampleExpandedPublicationFactory();
  }

  @Test
  void shouldPopulateHandlesWhenCandidateIsMissingHandles() {
    var handle = randomUri();
    var publication = publicationFactory.withHandle(handle).getExpandedPublication();
    var candidateId = createCandidateWithoutHandles(publication);

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.publicationDetails().handles()).containsExactly(handle);
  }

  @Test
  void shouldNotUpdateCandidateWhenHandlesArePresent() {
    var existingHandle = randomUri();
    var publication = publicationFactory.withHandle(randomUri()).getExpandedPublication();
    var candidateId = createCandidateWithHandles(publication, List.of(existingHandle));

    migrationService.migrateCandidate(candidateId);

    var candidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(candidate.publicationDetails().handles()).containsExactly(existingHandle);
  }

  @Test
  void shouldLeaveHandlesEmptyWhenPublicationHasNoHandle() {
    var publication = publicationFactory.getExpandedPublication();
    var candidateId = createCandidateWithoutHandles(publication);

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.publicationDetails().handles()).isEmpty();
  }

  private UUID createCandidateWithoutHandles(SampleExpandedPublication publication) {
    return createCandidate(publication, List.of());
  }

  private UUID createCandidateWithHandles(
      SampleExpandedPublication publication, List<URI> handles) {
    return createCandidate(publication, handles);
  }

  private UUID createCandidate(SampleExpandedPublication publication, List<URI> handles) {
    scenario.setupExpandedPublicationInS3(publication);
    var topLevelInstitution = randomOrganizationId();
    var dbDetails =
        randomPublicationBuilder(publication.identifier(), topLevelInstitution)
            .handles(handles)
            .build();
    var dbCandidate =
        randomCandidateBuilder(topLevelInstitution, dbDetails)
            .reportStatus(ReportStatus.REPORTED)
            .build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }
}
