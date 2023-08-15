package no.sikt.nva.nvi.common.service;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Status;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NviCandidateServiceTest {

    private static final String PUBLICATION_API_PATH = "publication";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");

    private NviCandidateService nviCandidateService;

    private FakeNviCandidateRepository fakeNviCandidateRepository;

    @BeforeEach
    void setup() {
        //TODO: Replave fakeNviCandidateRepository with actual repository when implemented
        fakeNviCandidateRepository = new FakeNviCandidateRepository();
        nviCandidateService = new NviCandidateService(fakeNviCandidateRepository);
    }

    @Test
    void shouldReturnCandidateIfCandidateIfCandidateWithIdExists() {
        var publicationId = generatePublicationId(UUID.randomUUID());
        var expectedCandidate = new Candidate.Builder()
                                    .withPublicationId(publicationId)
                                    .build();
        fakeNviCandidateRepository.save(expectedCandidate);
        assertThat(nviCandidateService.getCandidateByPublicationId(publicationId),
                   is(equalTo(Optional.of(expectedCandidate))));
    }

    @Test
    void shouldReturnTrueIfCandidateIfCandidateWithIdExists() {
        var publicationId = generatePublicationId(UUID.randomUUID());
        var expectedCandidate = new Candidate.Builder()
                                    .withPublicationId(publicationId)
                                    .build();
        fakeNviCandidateRepository.save(expectedCandidate);
        assertThat(nviCandidateService.exists(publicationId), is(equalTo(true)));
    }

    @Test
    void shouldCreateCandidateWithPendingInstitutionApprovals() {
        var publicationId = generatePublicationId(UUID.randomUUID());
        var institutionId = randomUri();
        var expectedCandidate = new Candidate.Builder()
                                    .withPublicationId(publicationId)
                                    .withApprovalStatuses(List.of(
                                        new ApprovalStatus.Builder().withInstitutionId(institutionId).withStatus(
                                            Status.PENDING).build()))
                                    .build();

        nviCandidateService.createCandidateWithPendingInstitutionApprovals(publicationId, List.of(institutionId));

        assertThat(fakeNviCandidateRepository.findByPublicationId(publicationId.toString()),
                   is(equalTo(Optional.of(expectedCandidate))));
    }

    private URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(PUBLICATION_API_PATH)
                   .addChild(identifier.toString())
                   .getUri();
    }
}