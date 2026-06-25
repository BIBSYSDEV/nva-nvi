package no.sikt.nva.nvi.index.utils;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.index.IndexDocumentHandler;
import no.sikt.nva.nvi.index.IndexDocumentScenario;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.test.uriretriever.CristinOrganizationFixtures;
import no.sikt.nva.nvi.test.uriretriever.FakeCristinOrganization;
import no.sikt.nva.nvi.test.uriretriever.FakeUriRetriever;
import no.unit.nva.s3.S3Driver;
import org.junit.jupiter.api.BeforeEach;

/**
 * Runs the shared generator test surface against the legacy JsonNode-based mapper. Wires up an S3
 * fake holding the expanded resource and a fake UriRetriever serving Cristin organization responses
 * for every NVI affiliation in the candidate.
 */
final class NviCandidateIndexDocumentGeneratorTest extends IndexDocumentGeneratorTestBase {

  private static final String CRISTIN_VERSION = "; version=2023-05-26";
  private static final String MEDIA_TYPE_JSON_V2 = "application/json" + CRISTIN_VERSION;
  private static final int HTTP_OK = 200;

  private S3Driver s3Reader;
  private FakeUriRetriever uriRetriever;

  @BeforeEach
  void mapperSpecificSetup() {
    s3Reader = new S3Driver(s3Client, BUCKET_NAME);
    uriRetriever = FakeUriRetriever.newInstance();
  }

  @Override
  protected IndexDocumentHandler handlerFor(IndexDocumentScenario scenario) {
    uploadExpandedResourceToS3(scenario);
    registerOrganizationsOnUriRetriever(scenario);
    return new IndexDocumentHandler(
        new S3StorageWriter(s3Client, BUCKET_NAME),
        new FakeSqsClient(),
        candidateService,
        new JsonNodeIndexDocumentGeneratorFactory(
            new S3StorageReader(s3Client, BUCKET_NAME), uriRetriever, ENVIRONMENT),
        ENVIRONMENT);
  }

  private void uploadExpandedResourceToS3(IndexDocumentScenario scenario) {
    var resourceIdentifier =
        scenario.candidate().publicationDetails().publicationIdentifier().toString();
    var resourceIndexDocument = dtoObjectMapper.createObjectNode();
    resourceIndexDocument.set("body", scenario.expandedResource());
    var path = TestScenario.constructPublicationBucketPath(resourceIdentifier);
    attempt(
            () ->
                s3Reader.insertFile(
                    path, dtoObjectMapper.writeValueAsString(resourceIndexDocument)))
        .orElseThrow();
  }

  private void registerOrganizationsOnUriRetriever(IndexDocumentScenario scenario) {
    scenario
        .candidate()
        .publicationDetails()
        .getNviCreatorAffiliations()
        .forEach(this::registerOrganizationHierarchy);
  }

  private void registerOrganizationHierarchy(URI affiliationId) {
    var leaf = FakeCristinOrganization.asLeafNode(affiliationId);
    var orgWithSubunits =
        CristinOrganizationFixtures.randomCristinOrganization(affiliationId)
            .withHasPart(List.of(leaf))
            .build();
    uriRetriever.registerResponse(
        affiliationId, HTTP_OK, MEDIA_TYPE_JSON_V2, orgWithSubunits.toJsonString());
  }
}
