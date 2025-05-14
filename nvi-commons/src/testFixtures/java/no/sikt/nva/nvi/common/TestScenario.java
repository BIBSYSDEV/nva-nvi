package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliations;
import static no.sikt.nva.nvi.test.TestConstants.PERSISTED_RESOURCES_BUCKET;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

public class TestScenario {
  private static final Environment ENVIRONMENT = new Environment();
  private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
  private final UriRetriever mockUriRetriever;
  private final OrganizationRetriever mockOrganizationRetriever;
  private final DynamoDbClient localDynamo;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final Organization defaultOrganization;
  private final S3Client s3Client;
  private final S3Driver s3Driver;

  public TestScenario() {
    this.localDynamo = initializeTestDatabase();
    this.candidateRepository = new CandidateRepository(localDynamo);
    this.periodRepository = new PeriodRepository(localDynamo);
    this.authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
    this.mockUriRetriever = mock(UriRetriever.class);
    this.mockOrganizationRetriever = new OrganizationRetriever(mockUriRetriever);
    this.defaultOrganization = setupTopLevelOrganizationWithSubUnits();

    s3Client = new FakeS3Client();
    s3Driver = new S3Driver(s3Client, ENVIRONMENT.readEnv(PERSISTED_RESOURCES_BUCKET));
  }

  public final Organization setupTopLevelOrganizationWithSubUnits() {
    var topLevelId = randomUriWithSuffix("topLevel");
    var subUnits = List.of(randomUriWithSuffix("subUnit1"), randomUriWithSuffix("subUnit2"));

    mockOrganizationResponseForAffiliations(topLevelId, subUnits, mockUriRetriever);
    return mockOrganizationRetriever.fetchOrganization(topLevelId);
  }

  public Environment getEnvironment() {
    return ENVIRONMENT;
  }

  public DynamoDbClient getLocalDynamo() {
    return localDynamo;
  }

  public CandidateRepository getCandidateRepository() {
    return candidateRepository;
  }

  public PeriodRepository getPeriodRepository() {
    return periodRepository;
  }

  public OrganizationRetriever getOrganizationRetriever() {
    return mockOrganizationRetriever;
  }

  public UriRetriever getMockedUriRetriever() {
    return mockUriRetriever;
  }

  public AuthorizedBackendUriRetriever getMockedAuthorizedBackendUriRetriever() {
    return authorizedBackendUriRetriever;
  }

  public S3Client getS3Client() {
    return s3Client;
  }

  public S3Driver getS3Driver() {
    return s3Driver;
  }

  public S3StorageReader getS3StorageReader() {
    return new S3StorageReader(s3Client, ENVIRONMENT.readEnv(PERSISTED_RESOURCES_BUCKET));
  }

  public Organization getDefaultOrganization() {
    return defaultOrganization;
  }

  public Candidate getCandidateByPublicationId(URI publicationId) {
    return Candidate.fetchByPublicationId(
        () -> publicationId, candidateRepository, periodRepository);
  }

  public Candidate upsertCandidate(UpsertNviCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  public Candidate updateApprovalStatus(
      Candidate candidate, ApprovalStatus status, URI topLevelOrganizationId) {
    var updateRequest = createUpdateStatusRequest(status, topLevelOrganizationId, randomString());
    return candidate.updateApprovalStatus(updateRequest, mockOrganizationRetriever);
  }

  public URI setupExpandedPublicationInS3(SampleExpandedPublication publication) {
    try {
      return s3Driver.insertFile(
          UnixPath.of(publication.identifier().toString()), publication.toJsonString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  public URI setupExpandedPublicationInS3(String publicationJson) {
    try {
      return s3Driver.insertFile(UnixPath.of(randomString()), publicationJson);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }
}
