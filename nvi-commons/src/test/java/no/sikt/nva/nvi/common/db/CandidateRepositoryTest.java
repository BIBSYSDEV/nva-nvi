package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
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
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateRepositoryTest extends LocalDynamoTest {

    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    public void setUp() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
    }

    @Test
    public void shouldThrowExceptionWhenAttemptingToSaveCandidateWithExistingPublicationId() {
        var publicationId = randomUri();
        var candidate1 = randomCandidateBuilder(true).publicationId(publicationId).build();
        var candidate2 = randomCandidateBuilder(true).publicationId(publicationId).build();
        candidateRepository.create(candidate1, List.of());
        assertThrows(RuntimeException.class, () -> candidateRepository.create(candidate2, List.of()));
        assertThat(scanDB().count(), is(equalTo(2)));
    }

    @Test
    public void shouldOverwriteExistingCandidateWhenUpdating() {
        var originalRequest = createUpsertCandidateRequest(randomUri());
        var candidate = Candidate.fromRequest(originalRequest, candidateRepository, periodRepository).orElseThrow();
        var originalDbCandidate = candidateRepository.findCandidateById(candidate.getIdentifier()).get().candidate();

        var newUpsertRequest = copyRequestWithNewInstanceType(originalRequest, randomValidInstanceType());
        Candidate.fromRequest(newUpsertRequest, candidateRepository, periodRepository).orElseThrow();
        var updatedDbCandidate = candidateRepository.findCandidateById(candidate.getIdentifier()).get().candidate();

        assertThat(scanDB().count(), is(equalTo(3)));
        assertThat(updatedDbCandidate, is(not(equalTo(originalDbCandidate))));
    }

    private static String randomValidInstanceType() {
        return randomInstanceTypeExcluding(
            InstanceType.NON_CANDIDATE).getValue();
    }

    private UpsertCandidateRequest copyRequestWithNewInstanceType(UpsertCandidateRequest request,
                                                                  String instanceType) {
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
            public String channelType() {
                return request.channelType();
            }

            @Override
            public URI publicationChannelId() {
                return request.publicationChannelId();
            }

            @Override
            public String level() {
                return request.level();
            }

            @Override
            public String instanceType() {
                return instanceType;
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
            public Map<URI, BigDecimal> institutionPoints() {
                return request.institutionPoints();
            }

            @Override
            public BigDecimal totalPoints() {
                return request.totalPoints();
            }
        };
    }
}