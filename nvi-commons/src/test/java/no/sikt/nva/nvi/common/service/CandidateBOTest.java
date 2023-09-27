package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.exception.NotFoundException;
import no.sikt.nva.nvi.common.service.requests.CreateNoteRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateBOTest extends LocalDynamoTest {

    private static final int YEAR = Year.now().getValue();
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
    }

    @Test
    void shouldThrowNotFoundExceptionWhenCandidateDoesNotExist() {
        assertThrows(NotFoundException.class, () -> CandidateBO.fromRequest(UUID::randomUUID,
                                                                            candidateRepository,
                                                                            periodRepository));
    }

    @Test
    void shouldReturnCandidateWhenExists() {
        var upsertCandidateRequest = createUpsertCandidateRequest();
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest,
                                                  candidateRepository,
                                                  periodRepository);
        var sameCand = CandidateBO.fromRequest(
            candidateBO::identifier, candidateRepository, periodRepository);
        assertThat(sameCand.identifier(), is(equalTo(candidateBO.identifier())));
    }

    @Test
    void shouldUpdateCandidateWhenChangingPoints() {
        var upsertCandidateRequest = createUpsertCandidateRequest();
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest,
                                                  candidateRepository,
                                                  periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidateBO.identifier(), candidateBO.toDto().publicationId(),
                                                         true, 2,
                                                         randomUri(), randomUri(), randomUri());
        var updatedCandidate = CandidateBO.fromRequest(
            updateRequest, candidateRepository, periodRepository);
        assertThat(updatedCandidate.identifier(), is(equalTo(candidateBO.identifier())));
        assertThat(updatedCandidate.toDto().approvalStatuses().size(), is(equalTo(3)));
    }

    @Test
    void shouldNotHaveApprovalsWhenNotApplicable() {
        var upsertCandidateRequest = createUpsertCandidateRequest();
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest,
                                                  candidateRepository,
                                                  periodRepository);
        var updateRequest = createUpsertCandidateRequest(candidateBO.identifier(), candidateBO.toDto().publicationId(),
                                                         false, 2,
                                                         randomUri());
        var updatedCandidate = CandidateBO.fromRequest(
            updateRequest, candidateRepository, periodRepository);
        assertThat(updatedCandidate.identifier(), is(equalTo(candidateBO.identifier())));
        assertThat(updatedCandidate.toDto().approvalStatuses().size(), is(equalTo(0)));
    }

    @Test
    void testFromRequest1() {
    }

    @Test
    void toDto() {
    }

    @Test
    void shouldUpdateStatusWhenRequestingAnUpdate() {
        var upsertCandidateRequest = createUpsertCandidateRequest();
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest,
                                                  candidateRepository,
                                                  periodRepository);
        CandidateBO updatedCandidate = candidateBO.updateStatus(
            new UpdateStatusRequest(upsertCandidateRequest.points().keySet().stream().findFirst().get(),
                                    DbStatus.APPROVED, randomString(), null));
        NviApprovalStatus status = updatedCandidate.toDto().approvalStatuses().get(0).status();

        assertThat(status, is(equalTo(NviApprovalStatus.APPROVED)));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var upsertCandidateRequest = createUpsertCandidateRequest();
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest,
                                                  candidateRepository,
                                                  periodRepository);
        candidateBO.updateStatus(
            new UpdateStatusRequest(upsertCandidateRequest.points().keySet().stream().findFirst().get(),
                                    DbStatus.APPROVED, randomString(), null));

        NviApprovalStatus status = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository,
                                                           periodRepository)
                                       .toDto()
                                       .approvalStatuses()
                                       .get(0)
                                       .status();

        assertThat(status, is(equalTo(NviApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneWhenRequestingChange() {

    }

    @Test
    void shouldCreateNoteWhenRequestingIt() {
        var upsertCandidateRequest = createUpsertCandidateRequest();
        var candidateBO = CandidateBO.fromRequest(upsertCandidateRequest,
                                                  candidateRepository,
                                                  periodRepository);
        int size = candidateBO.createNote(createNoteRequest(UUID.randomUUID(), randomString(), randomString()))
                       .toDto()
                       .notes()
                       .size();

        assertThat(size, is(1));
    }

    @Test
    void deleteNote() {
    }

    private static CreateNoteRequest createNoteRequest(UUID id, String text, String username) {
        return new CreateNoteRequest() {
            @Override
            public UUID identifier() {
                return id;
            }

            @Override
            public String text() {
                return text;
            }

            @Override
            public String username() {
                return username;
            }
        };
    }

    private static UpsertCandidateRequest createUpsertCandidateRequest() {
        return createUpsertCandidateRequest(UUID.randomUUID(), randomUri(), true, 1, randomUri());
    }

    private static UpsertCandidateRequest createUpsertCandidateRequest(UUID identifier,
                                                                       URI publicationId,
                                                                       boolean isApplicable,
                                                                       int creatorCount,
                                                                       URI... institutions) {
        var creators = IntStream.of(creatorCount)
                           .mapToObj(i -> randomUri())
                           .collect(Collectors.toMap(Function.identity(),
                                                     e -> List.of(institutions)));
        var points = Arrays.stream(institutions)
                         .collect(Collectors.toMap(Function.identity(), e -> randomBigDecimal()));
        return new UpsertCandidateRequest() {
            @Override
            public UUID identifier() {
                return identifier;
            }

            @Override
            public URI publicationBucketUri() {
                return randomUri();
            }

            @Override
            public URI publicationId() {
                return publicationId;
            }

            @Override
            public boolean isApplicable() {
                return isApplicable;
            }

            @Override
            public boolean isInternationalCooperation() {
                return false;
            }

            @Override
            public Map<URI, List<URI>> creators() {
                return creators;
            }

            @Override
            public String level() {
                return DbLevel.LEVEL_TWO.getValue();
            }

            @Override
            public String instanceType() {
                return InstanceType.ACADEMIC_MONOGRAPH.getValue();
            }

            @Override
            public PublicationDate publicationDate() {
                return new PublicationDate("2023", null, null);
            }

            @Override
            public Map<URI, BigDecimal> points() {
                return points;
            }

            @Override
            public int creatorCount() {
                return creatorCount;
            }
        };
    }
}