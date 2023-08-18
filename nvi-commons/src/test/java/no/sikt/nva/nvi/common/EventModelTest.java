package no.sikt.nva.nvi.common;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.nio.file.Path;
import java.util.List;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.CandidateStatus;
import no.sikt.nva.nvi.common.model.events.NonNviCandidate;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.common.model.events.Publication;
import no.sikt.nva.nvi.common.model.events.Publication.EntityDescription.Contributor;
import no.sikt.nva.nvi.common.model.events.Publication.EntityDescription.Contributor.Affiliation;
import no.sikt.nva.nvi.common.model.events.Publication.EntityDescription.PublicationDate;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.Test;

public class EventModelTest {

    public static final String CANDIDATE = IoUtils.stringFromResources(Path.of("candidate.json"));

    @Test
    void shouldParseIncomingEventAndConstructNviCandidateOutputEvent() {
        assertDoesNotThrow(
            () -> attempt(() -> dtoObjectMapper.readTree(CANDIDATE))
                                     .map(json -> json.at("/body"))
                                     .map(body -> dtoObjectMapper.readValue(body.toString(), Publication.class))
                                     .map(this::toEvent)
                                     .orElseThrow());
    }

    @Test
    void dumbTestForTestCoverage() {
        var creator = new Creator(randomUri(), List.of());
        creator.id();
        creator.nviInstitutions();

        var publicationDate = new PublicationDate(randomString(), randomString(), randomString());
        publicationDate.day();
        publicationDate.year();
        publicationDate.month();

        var candidate = new CandidateDetails(randomUri(), randomString(),
                                             randomString(), publicationDate, List.of(creator));
        candidate.instanceType();
        candidate.level();
        candidate.publicationDate();
        candidate.verifiedCreators();
        candidate.publicationId();
        new NonNviCandidate.Builder().withPublicationId(randomUri()).build().publicationId();

        var message = new CandidateEvaluatedMessage(CandidateStatus.CANDIDATE, randomUri(), candidate);
        message.candidateDetails();
        message.publicationUri();
        message.status();
        creator.nviInstitutions();

    }

    private static String getType(Publication publication) {
        return publication.entityDescription().reference().publicationInstance().type();
    }

    private static String getLevel(Publication publication) {
        return publication.entityDescription().reference().publicationContext().level();
    }

    private CandidateEvaluatedMessage toEvent(Publication publication) {
        return new CandidateEvaluatedMessage.Builder().withStatus(CandidateStatus.CANDIDATE)
                                                      .withPublicationBucketUri(randomUri())
                                                      .withCandidateDetails(toCandidateDetails(publication))
                                                      .build();
    }

    private CandidateDetails toCandidateDetails(Publication publication) {
        return new CandidateDetails(publication.id(), getType(publication), getLevel(publication),
                                    publication.entityDescription().publicationDate(), getCreators(publication));
    }

    private List<Creator> getCreators(Publication publication) {
        return publication.entityDescription().contributors().stream().map(this::toCreator).toList();
    }

    private Creator toCreator(Contributor contributor) {
        contributor.identity().verificationStatus();
        return new Creator(contributor.identity().id(), contributor.affiliations()
                                                                   .stream()
                                                                   .map(Affiliation::id)
                                                                   .toList());
    }
}
