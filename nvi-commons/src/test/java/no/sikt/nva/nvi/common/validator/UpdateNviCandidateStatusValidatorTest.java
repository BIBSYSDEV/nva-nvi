package no.sikt.nva.nvi.common.validator;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class UpdateNviCandidateStatusValidatorTest extends LocalDynamoTest {

    private static final URI DEFAULT_TOP_LEVEL_INSTITUTION_ID = URI.create(
        "https://www.example.com/toplevelOrganization");
    private static final URI DEFAULT_SUB_UNIT_INSTITUTION_ID = URI.create("https://www.example.com/subOrganization");
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private OrganizationRetriever organizationRetriever;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setUp() {
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        var mockUriRetriever = mock(UriRetriever.class);
        organizationRetriever = new OrganizationRetriever(mockUriRetriever);
        mockOrganizationResponseForAffiliation(DEFAULT_TOP_LEVEL_INSTITUTION_ID, DEFAULT_SUB_UNIT_INSTITUTION_ID,
                                               mockUriRetriever);
    }

    @Test
    void shouldReturnTrueForValidUpdateRequest() {
        var candidate = upsert(createUpsertCandidateRequest(DEFAULT_TOP_LEVEL_INSTITUTION_ID).build());
        var request = UpdateStatusRequest
                          .builder()
                          .withInstitutionId(DEFAULT_TOP_LEVEL_INSTITUTION_ID)
                          .withApprovalStatus(ApprovalStatus.APPROVED)
                          .withUsername(randomString())
                          .build();

        var isValid = UpdateNviCandidateStatusValidator.isValidUpdateStatusRequest(candidate,
                                                                                   request,
                                                                                   organizationRetriever);
        assertTrue(isValid);
    }

    @Test
    void shouldReturnFalseWhenCreatorsAreUnverified() {
        var unverifiedCreator = UnverifiedNviCreatorDto
                                    .builder()
                                    .withAffiliations(List.of(DEFAULT_SUB_UNIT_INSTITUTION_ID))
                                    .withName(randomString())
                                    .build();
        var candidate = upsert(createUpsertCandidateRequest(DEFAULT_TOP_LEVEL_INSTITUTION_ID)
                                   .withUnverifiedCreators(List.of(unverifiedCreator))
                                   .build());
        var request = UpdateStatusRequest
                          .builder()
                          .withInstitutionId(DEFAULT_TOP_LEVEL_INSTITUTION_ID)
                          .withApprovalStatus(ApprovalStatus.APPROVED)
                          .withUsername(randomString())
                          .build();

        var isValid = UpdateNviCandidateStatusValidator.isValidUpdateStatusRequest(candidate,
                                                                                   request,
                                                                                   organizationRetriever);
        assertFalse(isValid);
    }

    private Candidate upsert(UpsertCandidateRequest request) {
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }
}