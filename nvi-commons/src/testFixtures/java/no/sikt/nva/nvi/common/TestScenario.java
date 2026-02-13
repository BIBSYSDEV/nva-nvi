package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getGlobalEnvironment;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.CandidateAggregate;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.ApprovalService;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.NoteService;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

public class TestScenario {
  private final DynamoDbClient localDynamo;
  private final CandidateRepository candidateRepository;
  private final PeriodRepository periodRepository;
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

    s3Client = new FakeS3Client();
    s3Driver = new S3Driver(s3Client, EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET.getValue());
    s3StorageReader =
        new S3StorageReader(s3Client, EnvironmentFixtures.EXPANDED_RESOURCES_BUCKET.getValue());
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

  public S3Client getS3Client() {
    return s3Client;
  }

  public S3Driver getS3DriverForExpandedResourcesBucket() {
    return s3Driver;
  }

  public S3StorageReader getS3StorageReaderForExpandedResourcesBucket() {
    return s3StorageReader;
  }

  /**
   * Fetches all related DAOs for a Candidate, mirroring what is fetched and remapped to create the
   * business model for a Candidate class.
   */
  public CandidateAggregate getAllRelatedData(UUID candidateIdentifier) {
    var candidateFuture = candidateRepository.getCandidateAggregateAsync(candidateIdentifier);
    try {
      return candidateFuture.thenApply(CandidateAggregate::fromQueryResponse).get().orElseThrow();
    } catch (InterruptedException | ExecutionException exception) {
      throw new RuntimeException(exception);
    }
  }

  public Candidate getCandidateByIdentifier(UUID candidateIdentifier) {
    return candidateService.getCandidateByIdentifier(candidateIdentifier);
  }

  public Candidate getCandidateByPublicationId(URI publicationId) {
    return candidateService.getCandidateByPublicationId(publicationId);
  }

  public Candidate upsertCandidate(UpsertNviCandidateRequest request) {
    candidateService.upsertCandidate(request);
    return getCandidateByPublicationId(request.publicationId());
  }

  public Candidate updateApprovalStatus(
      UUID candidateIdentifier, UpdateStatusRequest updateRequest, UserInstance userInstance) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    var approvalService = new ApprovalService(candidateRepository);
    approvalService.updateApproval(candidate, updateRequest, userInstance);
    return getCandidateByIdentifier(candidate.identifier());
  }

  public Candidate updateApprovalStatus(
      UUID candidateIdentifier, ApprovalStatus status, URI topLevelOrganizationId) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    var updateRequest = createUpdateStatusRequest(status, topLevelOrganizationId, randomString());
    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    var approvalService = new ApprovalService(candidateRepository);
    approvalService.updateApproval(candidate, updateRequest, userInstance);
    return getCandidateByIdentifier(candidate.identifier());
  }

  public void createNote(UUID candidateIdentifier, String content, URI topLevelOrganizationId) {
    var candidate = getCandidateByIdentifier(candidateIdentifier);
    var noteRequest = new CreateNoteRequest(content, randomString(), topLevelOrganizationId);
    var noteService = new NoteService(candidateRepository);
    noteService.createNote(candidate, noteRequest);
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
