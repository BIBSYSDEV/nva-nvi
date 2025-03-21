package cucumber.steps;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

  @Given("a valid example document {string} in S3")
  public void aValidExampleDocumentInS3(String documentName) throws IOException {
    var s3Driver = new S3Driver(scenarioContext.getS3Client(), BUCKET_NAME);
    this.documentContent = stringFromResources(Path.of(documentName));
    this.documentLocation = s3Driver.insertFile(UnixPath.of(randomString()), documentContent);
  }

  @Given("an S3 URI to the document location")
  public void anS3URIToTheDocumentLocation() throws IOException {
    var s3Driver = new S3Driver(scenarioContext.getS3Client(), BUCKET_NAME);
    this.documentLocation = s3Driver.insertFile(UnixPath.of(randomString()), documentContent);
  }

  @When("a PublicationDto is created from the document")
  public void aPublicationDtoIsCreatedFromTheDocument() {
    var storageReader = new S3StorageReader(scenarioContext.getS3Client(), BUCKET_NAME);
    var dataLoader = new ExpandedPublicationTransformer(storageReader);
    this.publication = dataLoader.extractAndTransform(documentLocation);
  }

  @Then("the ID of the transformed document ends with {string}")
  public void theIdOfTheTransformedDocumentEndsWith(String expectedIdentifier) {
    var actualId = publication.id().toString();
    assertThat(actualId, endsWith(expectedIdentifier));
  }

  @Then("the PublicationDto has identifier {string}")
  public void thePublicationDtoHasIdentifier(String expectedValue) {
    assertEquals(expectedValue, publication.identifier());
  }

  @Then("the PublicationDto has the title {string}")
  public void thePublicationDtoHasTitle(String expectedValue) {
    assertEquals(expectedValue, publication.publicationTitle());
  }

  @Then("the PublicationDto has publication year {string}")
  public void thePublicationDtoHasPublicationYear(String expectedValue) {
    assertEquals(expectedValue, publication.publicationDate().year());
  }

  @Then("the PublicationDto has status {string}")
  public void thePublicationDtoHasStatus(String expectedValue) {
    assertEquals(expectedValue, publication.publicationStatus());
  }

  @Then("the PublicationDto has language {string}")
  public void thePublicationDtoHasLanguage(String expectedValue) {
    assertEquals(expectedValue, publication.publicationLanguage());
  }

  @Then("the PublicationDto has modified date {string}")
  public void thePublicationDtoHasModifiedDate(String expectedValue) {
    assertEquals(expectedValue, publication.modifiedDate().toString());
  }

  @Then("the PublicationDto is not an international collaboration")
  public void thePublicationDtoIsNotAnInternationalCollaboration() {
    assertFalse(publication.isInternationalCollaboration());
  }

  @Then("the PublicationDto has {int} contributor\\(s)")
  public void thePublicationDtoHasContributors(Integer expectedCount) {
    assertThat(publication.contributors(), hasSize(expectedCount));
  }

  @Then("the Contributor with ID {string} has the expected properties")
  public void theContributorWithIDHasTheExpectedProperties(String contributorId) {
    var contributor =
        publication.contributors().stream()
            .filter(c -> c.id().equals(URI.create(contributorId)))
            .findFirst()
            .orElseThrow();

    var expectedOrganizationId = "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0";
    var actualTopLevelOrganizationId = contributor.affiliations().getFirst().id();
    assertEquals(URI.create(contributorId), contributor.id());
    assertEquals("Mona Ullah", contributor.name());
    assertEquals("Verified", contributor.verificationStatus());
    assertEquals("Creator", contributor.role());
    assertEquals(URI.create(expectedOrganizationId), actualTopLevelOrganizationId);
  }
}
