package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.NviCandidate;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluateNviCandidateWithSyntheticDataTest extends EvaluationTest {
  private SampleExpandedPublicationFactory factory;
  private Organization nviOrganization1;
  private Organization nonNviOrganization;

  @BeforeEach
  void setup() {
    factory = new SampleExpandedPublicationFactory(authorizedBackendUriRetriever, uriRetriever);

    // Set up default organizations suitable for most test cases
    nviOrganization1 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    nonNviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);
  }

  // The parser should be able to handle documents with 10 000 contributors in 30 seconds.
  // This test case is a bit more generous because the GitHub Actions test runner is underpowered.
  @ParameterizedTest
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @ValueSource(ints = {100, 1_000, 5_000})
  void shouldParseDocumentWithManyContributorsWithinTimeOut(int numberOfForeignContributors) {
    var numberOfNorwegianContributors = 10;
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nonNviOrganization)
            .withRandomCreatorsAffiliatedWith(
                numberOfNorwegianContributors, COUNTRY_CODE_NORWAY, nviOrganization1)
            .withRandomCreatorsAffiliatedWith(
                numberOfForeignContributors, COUNTRY_CODE_SWEDEN, nonNviOrganization)
            .getExpandedPublication();

    var expectedCreatorShares = numberOfNorwegianContributors + numberOfForeignContributors;
    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.creatorShareCount()).isEqualTo(expectedCreatorShares);
  }

  private URI addPublicationToS3(SampleExpandedPublication publication) {
    try {
      return s3Driver.insertFile(
          UnixPath.of(publication.identifier().toString()), publication.toJsonString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  private CandidateEvaluatedMessage getMessageBody() {
    try {
      var sentMessages = queueClient.getSentMessages();
      var message = sentMessages.getFirst();
      return objectMapper.readValue(message.messageBody(), CandidateEvaluatedMessage.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private NviCandidate getEvaluatedCandidate(SampleExpandedPublication publication) {
    var fileUri = addPublicationToS3(publication);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    return (NviCandidate) getMessageBody().candidate();
  }
}
