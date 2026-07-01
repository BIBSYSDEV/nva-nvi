package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApprovalDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.IndexDocumentHandler;
import no.sikt.nva.nvi.index.IndexDocumentScenario;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the shared generator test surface against the new SPARQL/DTO-based mapper. Wires up a mocked
 * {@link PublicationLoaderService} that returns the scenario's {@code PublicationDto} for the
 * candidate's bucket URI. Also holds mapper-specific cases that have no equivalent in the legacy
 * mapper, such as the channel-by-type fallback.
 */
final class CandidateToIndexDocumentMapperTest extends IndexDocumentGeneratorTestBase {

  private PublicationLoaderService publicationLoaderService;

  @BeforeEach
  void mapperSpecificSetup() {
    publicationLoaderService = mock(PublicationLoaderService.class);
  }

  @Override
  protected IndexDocumentHandler handlerFor(IndexDocumentScenario scenario) {
    when(publicationLoaderService.extractAndTransform(
            scenario.candidate().publicationDetails().publicationBucketUri()))
        .thenReturn(scenario.publicationDto());
    return new IndexDocumentHandler(
        new S3StorageWriter(s3Client, BUCKET_NAME),
        new FakeSqsClient(),
        candidateService,
        new PublicationDtoIndexDocumentGeneratorFactory(publicationLoaderService, ENVIRONMENT),
        ENVIRONMENT);
  }

  @Test
  void shouldMatchChannelByTypeWhenCandidateChannelHasNoId() {
    var institutionId = randomUri();
    var candidate = candidateWithChannelTypeButNoId(institutionId);
    var expectedName = randomString();
    var channelDto =
        PublicationChannelDto.builder()
            .withId(randomUri())
            .withChannelType(ChannelType.JOURNAL)
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .withName(expectedName)
            .build();
    var publicationDto =
        PublicationDto.builder()
            .withId(candidate.getPublicationId())
            .withStatus("PUBLISHED")
            .withPublicationChannels(List.of(channelDto))
            .build();

    var document =
        new CandidateToIndexDocumentMapper(candidate, publicationDto, ENVIRONMENT).generate();

    assertThat(document.publicationDetails().publicationChannel().name()).isEqualTo(expectedName);
  }

  private Candidate candidateWithChannelTypeButNoId(URI institutionId) {
    var channel =
        new DbPublicationChannel(
            null, ChannelType.JOURNAL.getValue(), ScientificValue.LEVEL_ONE.getValue());
    var publicationDetails = randomPublicationBuilder(institutionId).build();
    var pointCalculation =
        randomPointCalculationBuilder(randomUri(), institutionId)
            .publicationChannel(channel)
            .build();
    var dao =
        createCandidateDao(
            randomCandidateBuilder(institutionId, publicationDetails, pointCalculation).build());
    var approvals = List.of(randomApprovalDao(dao.identifier(), institutionId));
    candidateRepository.create(dao, approvals);
    return candidateService.getCandidateByIdentifier(dao.identifier());
  }
}
