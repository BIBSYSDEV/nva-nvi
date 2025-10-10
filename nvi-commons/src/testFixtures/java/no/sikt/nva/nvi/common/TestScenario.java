package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliations;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.CandidateAggregate;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class TestScenario {
  private final AuthorizedBackendUriRetriever authorizedBackendUriRetriever;
  private final UriRetriever mockUriRetriever;
  private final OrganizationRetriever mockOrganizationRetriever;
  private final DynamoDbClient localDynamo;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
  private final Organization defaultOrganization;
  private final FakeS3Client s3Client;
  private final S3Driver s3Driver;
  private final S3StorageReader s3StorageReader;
  private final NviPeriodService periodService;
  private final CandidateService candidateService;

  public TestScenario() {
    localDynamo = initializeTestDatabase();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = new PeriodRepository(localDynamo);
    periodService = new NviPeriodService(getGlobalEnvironment(), periodRepository);
    candidateService =
        new CandidateService(getGlobalEnvironment(), periodRepository, candidateRepository);

    authorizedBackendUriRetriever = mock(AuthorizedBackendUriRetriever.class);
    mockUriRetriever = mock(UriRetriever.class);
    mockOrganizationRetriever = new OrganizationRetriever(mockUriRetriever);
    defaultOrganization = setupTopLevelOrganizationWithSubUnits();

    s3Client = new FakeS3Client();
    s3Driver = new S3Driver(s3Client, EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET.getValue());
    s3StorageReader =
        new S3StorageReader(s3Client, EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET.getValue());
  }

  public final Organization setupTopLevelOrganizationWithSubUnits() {
    var topLevelId = randomUriWithSuffix("topLevel");
    var subUnits = List.of(randomUriWithSuffix("subUnit1"), randomUriWithSuffix("subUnit2"));

    mockOrganizationResponseForAffiliations(topLevelId, subUnits, mockUriRetriever);
    return mockOrganizationRetriever.fetchOrganization(topLevelId);
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

  public CandidateService getCandidateService() {
    return candidateService;
  }

  public NviPeriodService getPeriodService() {
    return periodService;
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

  public S3Driver getS3DriverForExpandedResourcesBucket() {
    return s3Driver;
  }

  public S3StorageReader getS3StorageReaderForExpandedResourcesBucket() {
    return s3StorageReader;
  }

  public Organization getDefaultOrganization() {
    return defaultOrganization;
  }

  /**
   * Fetches all related DAOs for a Candidate, mirroring what is fetched and remapped to create the
   * business model for a Candidate class.
   */
  public CandidateAggregate getAllRelatedData(UUID candidateIdentifier) {
    return candidateRepository
        .getCandidateAggregate(candidateIdentifier)
        .candidateAggregate()
        .orElseThrow();
  }

  public Candidate getCandidateByIdentifier(UUID candidateIdentifier) {
    return candidateService.fetch(candidateIdentifier);
  }

  public Candidate getCandidateByPublicationId(URI publicationId) {
    return candidateService.fetchByPublicationId(publicationId);
  }

  public Candidate upsertCandidate(UpsertNviCandidateRequest request) {
    Candidate.upsert(request, candidateRepository);
    return getCandidateByPublicationId(request.publicationId());
  }

  public void updateApprovalStatusDangerously(
      Candidate candidate, ApprovalStatus status, URI topLevelOrganizationId) {
    var updateRequest = createUpdateStatusRequest(status, topLevelOrganizationId, randomString());
    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    candidate.updateApprovalStatus(updateRequest, userInstance);
  }

  public Candidate updateApprovalStatus(
      UUID candidateIdentifier, UpdateStatusRequest updateRequest, UserInstance userInstance) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    candidate.updateApprovalStatus(updateRequest, userInstance);
    return getCandidateByIdentifier(candidate.getIdentifier());
  }

  public Candidate updateApprovalStatus(
      UUID candidateIdentifier, ApprovalStatus status, URI topLevelOrganizationId) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    var updateRequest = createUpdateStatusRequest(status, topLevelOrganizationId, randomString());
    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    candidate.updateApprovalStatus(updateRequest, userInstance);
    return getCandidateByIdentifier(candidate.getIdentifier());
  }

  public Candidate updateApprovalAssignee(
      UUID candidateIdentifier, UpdateAssigneeRequest updateRequest) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    candidate.updateApprovalAssignee(updateRequest);
    return getCandidateByIdentifier(candidate.getIdentifier());
  }

  public void createNote(UUID candidateIdentifier, String content, URI topLevelOrganizationId) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    var noteRequest = new CreateNoteRequest(content, randomString(), topLevelOrganizationId);
    candidate.createNote(noteRequest, candidateRepository);
  }

  public URI setupExpandedPublicationInS3(SampleExpandedPublication publication) {
    try {
      return s3Driver.insertFile(
          constructPublicationBucketPath(publication.identifier().toString()),
          publication.toJsonString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  public URI setupExpandedPublicationInS3(String publicationJson) {
    try {
      return s3Driver.insertFile(constructPublicationBucketPath(randomString()), publicationJson);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  public static UnixPath constructPublicationBucketPath(String publicationIdentifier) {
    return UnixPath.of("resources", publicationIdentifier + ".gz");
  }
}
