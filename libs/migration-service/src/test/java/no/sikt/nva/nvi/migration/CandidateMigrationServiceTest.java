package no.sikt.nva.nvi.migration;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateInRepository;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.mapToContributorDto;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomContributorDtoBuilder;
import static no.sikt.nva.nvi.common.model.NviCreatorFixtures.verifiedNviCreatorFrom;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
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
    publicationFactory = new SampleExpandedPublicationFactory();
  }

  @Test
  void shouldMigrateCreatorNames() {
    var nviOrg = publicationFactory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var creator = verifiedNviCreatorFrom(nviOrg);
    var publication =
        publicationFactory.withContributor(mapToContributorDto(creator)).getExpandedPublication();

    var affiliations = List.copyOf(creator.getAffiliationIds());
    var creatorWithoutName = new DbCreator(creator.id(), null, null, affiliations);
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
  void shouldMigrateCreatorOrcid() {
    var nviOrg = publicationFactory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var creator = verifiedNviCreatorFrom(nviOrg, nviOrg.id());
    var publication =
        publicationFactory.withContributor(mapToContributorDto(creator)).getExpandedPublication();

    var creatorWithoutOrcid =
        new DbCreator(creator.id(), creator.name(), null, List.copyOf(creator.getAffiliationIds()));
    var candidateId =
        createLegacyCandidate(
            publication, builder -> builder.creators(List.of(creatorWithoutOrcid)));

    migrationService.migrateCandidate(candidateId);

    var updatedCreator = getCreatorById(candidateId, creator.id());
    assertThat(updatedCreator.orcid()).isEqualTo(creator.orcid());
  }

  @Test
  void shouldNotOverwriteExistingCreatorData() {
    var nviOrg = publicationFactory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    var creator = verifiedNviCreatorFrom(nviOrg, nviOrg.id());
    var contributorWithDifferentData =
        randomContributorDtoBuilder(nviOrg).withId(creator.id()).build();
    var publication =
        publicationFactory.withContributor(contributorWithDifferentData).getExpandedPublication();

    var candidateId =
        createLegacyCandidate(
            publication, builder -> builder.creators(List.of(creator.toDbCreatorType())));

    migrationService.migrateCandidate(candidateId);

    var updatedCreator = getCreatorById(candidateId, creator.id());
    assertThat(updatedCreator.name()).isEqualTo(creator.name());
    assertThat(updatedCreator.orcid()).isEqualTo(creator.orcid());
  }

  @Test
  void shouldPreserveCreatorNotFoundInPublication() {
    var orphanCreatorId = randomUri();
    var orphanCreator = new DbCreator(orphanCreatorId, null, null, List.of(randomUri()));

    var candidateId =
        createLegacyCandidate(
            publicationFactory.getExpandedPublication(),
            builder -> builder.creators(List.of(orphanCreator)));

    migrationService.migrateCandidate(candidateId);

    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateId);
    assertThat(updatedCandidate.publicationDetails().nviCreators())
        .anyMatch(creator -> orphanCreatorId.equals(creator.id()));
  }

  private NviCreator getCreatorById(UUID candidateIdentifier, URI creatorId) {
    var updatedCandidate = candidateService.getCandidateByIdentifier(candidateIdentifier);
    return updatedCandidate.publicationDetails().nviCreators().stream()
        .filter(creator -> creatorId.equals(creator.id()))
        .findFirst()
        .orElseThrow();
  }

  private UUID createLegacyCandidate(
      SampleExpandedPublication publication,
      Function<DbCandidate.Builder, DbCandidate.Builder> customizer) {
    scenario.setupExpandedPublicationInS3(publication);
    var topLevelInstitution = randomOrganizationId();
    var dbDetails = randomPublicationBuilder(publication.identifier(), topLevelInstitution);
    var builder =
        randomCandidateBuilder(topLevelInstitution, dbDetails.build())
            .reportStatus(ReportStatus.REPORTED)
            .reportedDate(Instant.now());
    var dbCandidate = customizer.apply(builder).build();
    return createCandidateInRepository(candidateRepository, dbCandidate);
  }
}
