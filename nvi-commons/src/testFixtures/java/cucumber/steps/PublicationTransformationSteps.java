package cucumber.steps;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;

import cucumber.contexts.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.etl.ExpandedPublication;
import no.sikt.nva.nvi.common.etl.ExpandedPublicationTransformer;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.paths.UnixPath;

public class PublicationTransformationSteps {
  private static final String BUCKET_NAME = "testBucket";
  private final ScenarioContext scenarioContext;
  private String documentContent;
  private URI documentLocation;
  private ExpandedPublication publication;

  public PublicationTransformationSteps(ScenarioContext scenarioContext) {
    this.scenarioContext = scenarioContext;
  }

  @Given("a valid example document {string}")
  public void aValidExampleDocument(String documentName) {
    this.documentContent = stringFromResources(Path.of(documentName));
  }

  @Given("an S3 URI to the document location")
  public void anS3URIToTheDocumentLocation() throws IOException {
    var s3Driver = new S3Driver(scenarioContext.getS3Client(), BUCKET_NAME);
    this.documentLocation = s3Driver.insertFile(UnixPath.of(randomString()), documentContent);
  }

  @When("the document is extracted and transformed")
  public void theDocumentIsExtractedAndTransformed() {
    var storageReader = new S3StorageReader(scenarioContext.getS3Client(), BUCKET_NAME);
    var dataLoader = new ExpandedPublicationTransformer(storageReader);
    this.publication = dataLoader.extractAndTransform(documentLocation);
  }

  @Then("the ID of the transformed document ends with {string}")
  public void theIdOfTheTransformedDocumentEndsWith(String expectedIdentifier) {
    var actualId = publication.id().toString();
    assertThat(actualId, endsWith(expectedIdentifier));
  }
}
