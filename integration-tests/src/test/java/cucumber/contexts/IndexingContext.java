package cucumber.contexts;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getIndexDocumentHandlerEnvironment;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.cristin.FakeCristinOrganization;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.IndexDocumentHandler;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.sikt.nva.nvi.test.uriretriever.FakeUriRetriever;
import no.unit.nva.stubs.FakeContext;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;

/**
 * Drives the real {@link IndexDocumentHandler} over a shared {@link TestScenario}. The Candidate is
 * created through the regular evaluation flow (see {@link EvaluationContext}); this context only
 * fakes the organization registry (via {@link FakeUriRetriever}). DynamoDB, S3, and the index
 * document generation all run for real, so the generated document reflects production behaviour.
 */
public class IndexingContext {

  private static final Context HANDLER_CONTEXT = new FakeContext();
  private static final String MEDIA_TYPE = "application/json; version=2023-05-26";
  private static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
  private static final String GZIP_ENDING = ".gz";

  private final TestScenario scenario;

  // Stubs the Cristin organization registry. Unlike evaluation (which reads the org hierarchy from
  // the expanded publication), the index document generator looks up each NVI affiliation's
  // hierarchy live against the Cristin API (OrganizationRetriever/UriRetriever), ignoring the
  // partOf already present in the publication. So indexing tests must register a canned org
  // response per affiliation instead of relying on the input document. This live-lookup fragility
  // is the tech debt behind NP-51406 and NP-51432; once indexing derives org data from persisted
  // data, this stub becomes unnecessary.
  private final FakeUriRetriever uriRetriever;

  private final IndexDocumentHandler indexHandler;

  public IndexingContext(TestScenario scenario) {
    this.scenario = scenario;
    this.uriRetriever = FakeUriRetriever.newInstance();
    this.indexHandler =
        new IndexDocumentHandler(
            scenario.getS3StorageReaderForExpandedResourcesBucket(),
            new S3StorageWriter(scenario.getS3Client(), EXPANDED_RESOURCES_BUCKET.getValue()),
            new FakeSqsClient(),
            scenario.getCandidateService(),
            uriRetriever,
            getIndexDocumentHandlerEnvironment());
  }

  /**
   * Registers a canned Cristin organization response for the id the index document generator will
   * fetch when it expands an affiliation. The organization must carry its full nested {@code
   * partOf} chain (see {@code CristinOrganizationFixtures.organizationWithNestedPartOf}), because
   * the generator walks partOf within this single fetched document rather than re-fetching each
   * parent.
   */
  public void registerOrganization(FakeCristinOrganization organization) {
    uriRetriever.registerResponse(
        organization.id(), HttpURLConnection.HTTP_OK, MEDIA_TYPE, organization.toJsonString());
  }

  /**
   * Overwrites the source publication in S3 without re-evaluating the candidate, simulating a
   * source correction that the frozen candidate never sees. Writes to the candidate's actual
   * publication bucket URI so the index handler reads the changed content on the next reindex.
   */
  public void overwriteSource(Candidate candidate, SampleExpandedPublication publication) {
    var path =
        UriWrapper.fromUri(candidate.publicationDetails().publicationBucketUri()).toS3bucketPath();
    attempt(
            () ->
                scenario
                    .getS3DriverForExpandedResourcesBucket()
                    .insertFile(path, publication.toJsonString()))
        .orElseThrow();
  }

  public void index(Candidate candidate) {
    indexHandler.handleRequest(createEvent(candidate.identifier()), HANDLER_CONTEXT);
  }

  public IndexDocumentWithConsumptionAttributes readIndexDocument(Candidate candidate) {
    var path = UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(candidate.identifier() + GZIP_ENDING);
    var content = scenario.getS3DriverForExpandedResourcesBucket().getFile(path);
    return attempt(
            () -> dtoObjectMapper.readValue(content, IndexDocumentWithConsumptionAttributes.class))
        .orElseThrow();
  }
}
