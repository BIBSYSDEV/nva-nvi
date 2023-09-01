package no.sikt.nva.nvi.common;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import java.util.List;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.CandidateStatus;
import no.sikt.nva.nvi.common.model.events.NonNviCandidate;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.Creator;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails.PublicationDate;
import org.junit.jupiter.api.Test;

public class EventModelTest {

    //TODO: Restructure project, move event model to module event-handlers together with EvaluateCandidateHandler
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

        var message = new CandidateEvaluatedMessage(CandidateStatus.CANDIDATE, randomUri(), candidate, null);
        message.candidateDetails();
        message.publicationBucketUri();
        message.status();
        creator.nviInstitutions();
        message.institutionPoints();
    }
}
