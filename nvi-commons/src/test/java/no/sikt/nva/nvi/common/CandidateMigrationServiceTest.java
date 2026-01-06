package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.mapToContributorDto;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.verifiedNviCreatorFrom;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateMigrationServiceTest {

  private TestScenario scenario;
  private CandidateService candidateService;
  private CandidateRepository candidateRepository;
  private CandidateMigrationService migrationService;
  private SampleExpandedPublicationFactory publicationFactory;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    candidateService = scenario.getCandidateService();
    candidateRepository = scenario.getCandidateRepository();
    var storageReader = scenario.getS3StorageReaderForExpandedResourcesBucket();
    migrationService = new CandidateMigrationService(candidateService, storageReader);
    setupOpenPeriod(scenario, CURRENT_YEAR);
    publicationFactory = new SampleExpandedPublicationFactory(scenario);
  }

  @Test
  void shouldMigrateCreatorNames() {
    var nviOrg = publicationFactory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var creator = verifiedNviCreatorFrom(nviOrg, nviOrg.id());
    var publication =
        publicationFactory.withContributor(mapToContributorDto(creator)).getExpandedPublication();

    var creatorWithoutName = new DbCreator(creator.id(), null, creator.getAffiliationIds());
    var candidateId =
        createLegacyCandidate(
            publication, builder -> builder.creators(List.of(creatorWithoutName)));

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.publicationDetails().nviCreators())
        .extracting(NviCreator::name)
        .containsOnlyOnce(creator.name());
  }

  @Test
  void shouldPreserveCreatorNotFoundInPublication() {
    var orphanCreatorId = randomUri();
    var orphanCreator = new DbCreator(orphanCreatorId, null, List.of(randomUri()));

    var candidateId =
        createLegacyCandidate(
            publicationFactory.getExpandedPublication(),
            builder -> builder.creators(List.of(orphanCreator)));

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.publicationDetails().nviCreators())
        .anyMatch(creator -> orphanCreatorId.equals(creator.id()));
  }

  private UUID createLegacyCandidate(
      SampleExpandedPublication publication,
      Function<DbCandidate.Builder, DbCandidate.Builder> customizer) {
    var publicationBucketUri = scenario.setupExpandedPublicationInS3(publication);
    var builder =
        randomCandidateBuilder(true)
            .reportStatus(ReportStatus.REPORTED)
            .publicationId(publication.id())
            .publicationBucketUri(publicationBucketUri);
    var dbCandidate = customizer.apply(builder).build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }
}
