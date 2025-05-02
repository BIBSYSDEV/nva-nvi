package no.sikt.nva.nvi.events.evaluator;

import static no.sikt.nva.nvi.events.evaluator.TestUtils.createEvent;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_NORWAY;
import static no.sikt.nva.nvi.test.TestConstants.COUNTRY_CODE_SWEDEN;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PageCountDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.sikt.nva.nvi.events.persist.UpsertNviCandidateHandler;
import no.sikt.nva.nvi.test.SampleExpandedPublication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluateNviCandidateWithSyntheticDataTest extends EvaluationTest {
  private SampleExpandedPublicationFactory factory;
  private Organization nviOrganization1;
  private Organization nonNviOrganization;
  private UpsertNviCandidateHandler upsertNviCandidateHandler;
  private QueueClient dlqClient;

  @BeforeEach
  void setup() {
    upsertNviCandidateHandler =
        new UpsertNviCandidateHandler(
            candidateRepository, periodRepository, dlqClient, ENVIRONMENT);

    factory = new SampleExpandedPublicationFactory(authorizedBackendUriRetriever, uriRetriever);

    // Set up default organizations suitable for most test cases
    nviOrganization1 = factory.setupTopLevelOrganization(COUNTRY_CODE_NORWAY, true);
    nonNviOrganization = factory.setupTopLevelOrganization(COUNTRY_CODE_SWEDEN, false);
  }

  // The parser should be able to handle documents with 10 000 contributors in 30 seconds.
  // This test case is a bit more generous because the GitHub Actions test runner is underpowered.
  @ParameterizedTest
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  @ValueSource(ints = {100}) // FIXME
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
    var candidate = evaluatePublication(publication);
    assertThat(candidate.getCreatorShareCount()).isEqualTo(expectedCreatorShares);
  }

  @Test
  void shouldIncludeAbstract() {
    // TODO: Not implemented
    var expectedAbstract = "Lorem ipsum";
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nonNviOrganization)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1)
            .getExpandedPublicationBuilder()
            .withAbstract(expectedAbstract)
            .build();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.abstractText()).isEqualTo(expectedAbstract);
  }

  @ParameterizedTest
  @MethodSource("pageCountProvider")
  void shouldIncludePageCount(
      PageCountDto expectedPageCount, String publicationType, String channelType) {
    // TODO: Not implemented
    var publication =
        factory
            .withTopLevelOrganizations(nviOrganization1, nonNviOrganization)
            .withNorwegianCreatorAffiliatedWith(nviOrganization1)
            .withPublicationChannel(channelType, "LevelOne")
            .getExpandedPublicationBuilder()
            .withAbstract("Lorem ipsum")
            .withInstanceType(publicationType)
            .withPageCount(
                expectedPageCount.firstPage(),
                expectedPageCount.lastPage(),
                expectedPageCount.numberOfPages())
            .build();

    var candidate = getEvaluatedCandidate(publication);
    assertThat(candidate.pageCount()).isEqualTo(expectedPageCount);
  }

  private static Stream<Arguments> pageCountProvider() {
    return Stream.of(
        argumentSet(
            "Monograph with page count",
            new PageCountDto(null, null, "789"),
            "AcademicMonograph",
            "Series"),
        argumentSet(
            "Article with page range",
            new PageCountDto("123", "456", null),
            "AcademicArticle",
            "Journal"));
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

  private UpsertNviCandidateRequest getEvaluatedCandidate(SampleExpandedPublication publication) {
    var fileUri = scenario.addPublicationToS3(publication);
    var event = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(event, CONTEXT);
    return (UpsertNviCandidateRequest) getMessageBody().candidate();
  }

  /**
   * Evaluates a publication as if it was stored in S3 and returns the candidate from the database.
   * This wrapper is an abstraction of the whole processing chain in `event-handlers`, including
   * parsing, evaluation, and upsert.
   */
  private Candidate evaluatePublication(SampleExpandedPublication publication) {
    var fileUri = scenario.addPublicationToS3(publication);
    var evaluationEvent = createEvent(new PersistedResourceMessage(fileUri));
    handler.handleRequest(evaluationEvent, CONTEXT);

    var upsertEvent = createUpsertEvent(getMessageBody());
    upsertNviCandidateHandler.handleRequest(upsertEvent, CONTEXT);

    return Candidate.fetchByPublicationId(publication::id, candidateRepository, periodRepository);
  }

  private SQSEvent createUpsertEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    var body =
        attempt(() -> objectMapper.writeValueAsString(candidateEvaluatedMessage)).orElseThrow();
    message.setBody(body);
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }
}
