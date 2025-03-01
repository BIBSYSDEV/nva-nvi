package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.initializeTestDatabase;
import static no.sikt.nva.nvi.common.LocalDynamoTestSetup.scanDB;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class CandidateRepositoryTest {

  private DynamoDbClient localDynamo;
  private CandidateRepository candidateRepository;
  private PeriodRepository periodRepository;

  @BeforeEach
  void setUp() {
    localDynamo = initializeTestDatabase();
    candidateRepository = new CandidateRepository(localDynamo);
    periodRepository = new PeriodRepository(localDynamo);
  }

  @Test
  void shouldThrowExceptionWhenAttemptingToSaveCandidateWithExistingPublicationId() {
    var publicationId = randomUri();
    var candidate1 = randomCandidateBuilder(true).publicationId(publicationId).build();
    var candidate2 = randomCandidateBuilder(true).publicationId(publicationId).build();
    candidateRepository.create(candidate1, List.of());
    assertThrows(RuntimeException.class, () -> candidateRepository.create(candidate2, List.of()));
    assertThat(scanDB(localDynamo).count(), is(equalTo(2)));
  }

  @Test
  void shouldOverwriteExistingCandidateWhenUpdating() {
    var originalRequest = createUpsertCandidateRequest(randomUri()).build();
    Candidate.upsert(originalRequest, candidateRepository, periodRepository);
    var candidateDao =
        candidateRepository.findByPublicationId(originalRequest.publicationId()).get();
    var originalDbCandidate = candidateDao.candidate();

    var newUpsertRequest = copyRequestWithNewInstanceType(originalRequest, randomUri());
    Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository);
    var updatedDbCandidate =
        candidateRepository.findCandidateById(candidateDao.identifier()).get().candidate();

    assertThat(scanDB(localDynamo).count(), is(equalTo(3)));
    assertThat(updatedDbCandidate, is(not(equalTo(originalDbCandidate))));
  }

  // FIXME: This is duplicated and probably not needed
  private UpsertCandidateRequest copyRequestWithNewInstanceType(
      UpsertCandidateRequest request, URI publicationChannelId) {
    return new UpsertCandidateRequest() {
      @Override
      public URI publicationBucketUri() {
        return request.publicationBucketUri();
      }

      @Override
      public URI publicationId() {
        return request.publicationId();
      }

      @Override
      public boolean isApplicable() {
        return request.isApplicable();
      }

      @Override
      public boolean isInternationalCollaboration() {
        return request.isInternationalCollaboration();
      }

      @Override
      public Map<URI, List<URI>> creators() {
        return request.creators();
      }

      @Override
      public List<VerifiedNviCreatorDto> verifiedCreators() {
        return request.verifiedCreators();
      }

      @Override
      public List<UnverifiedNviCreatorDto> unverifiedCreators() {
        return request.unverifiedCreators();
      }

      @Override
      public String channelType() {
        return request.channelType();
      }

      @Override
      public URI publicationChannelId() {
        return publicationChannelId;
      }

      @Override
      public String level() {
        return request.level();
      }

      @Override
      public InstanceType instanceType() {
        return request.instanceType();
      }

      @Override
      public PublicationDate publicationDate() {
        return request.publicationDate();
      }

      @Override
      public int creatorShareCount() {
        return request.creatorShareCount();
      }

      @Override
      public BigDecimal collaborationFactor() {
        return request.collaborationFactor();
      }

      @Override
      public BigDecimal basePoints() {
        return request.basePoints();
      }

      @Override
      public List<InstitutionPoints> institutionPoints() {
        return request.institutionPoints();
      }

      @Override
      public BigDecimal totalPoints() {
        return request.totalPoints();
      }
    };
  }
}
