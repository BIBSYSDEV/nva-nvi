package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
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
        var candidate = Candidate.upsert(originalRequest, candidateRepository, periodRepository).orElseThrow();
        var originalDbCandidate = candidateRepository.findCandidateById(candidate.getIdentifier()).get().candidate();

        var newUpsertRequest = copyRequestWithNewInstanceType(originalRequest, randomUri());
        Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository).orElseThrow();
        var updatedDbCandidate = candidateRepository.findCandidateById(candidate.getIdentifier()).get().candidate();

        assertThat(scanDB().count(), is(equalTo(3)));
        assertThat(updatedDbCandidate, is(not(equalTo(originalDbCandidate))));
    }

    @Test
    void shouldSerializeCandidateWithEmpty() throws JsonProcessingException {
        DbCandidate candidate = randomCandidate().copy().points(List.of()).build();

        candidateRepository.create(candidate, List.of());

        var can = candidateRepository.scanEntries(10, null, List.of())
                      .getDatabaseEntries()
                      .stream()
                      .toList();
        var s = "";
    }

    private UpsertCandidateRequest copyRequestWithNewInstanceType(UpsertCandidateRequest request,
                                                                  URI publicationChannelId) {
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
                return publicationChannelId;
            }

            @Override
            public String level() {
                return request.level();
            }

            @Override
            public String instanceType() {
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