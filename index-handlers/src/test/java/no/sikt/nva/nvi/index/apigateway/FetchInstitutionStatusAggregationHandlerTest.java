package no.sikt.nva.nvi.index.apigateway;

import static java.util.function.Predicate.not;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getFetchInstitutionStatusAggregationHandlerEnvironment;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomIndexDocumentBuilder;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomPublicationDetailsBuilder;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.hasEqualValue;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.toBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.model.ApprovalFactory;
import no.sikt.nva.nvi.index.model.document.Approval;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.InstitutionPoints;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.zalando.problem.Problem;
import org.zalando.problem.StatusType;

class FetchInstitutionStatusAggregationHandlerTest {

  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static final Context CONTEXT = mock(Context.class);
  private static final Environment ENVIRONMENT =
      getFetchInstitutionStatusAggregationHandlerEnvironment();
  private String username;
  private URI userTopLevelOrg;
  private AccessRight userAccessRight;
  private String queryYear;
  private FetchInstitutionStatusAggregationHandler handler;
  private ByteArrayOutputStream output;

  private static final String YEAR = "year";
  private static final URI OUR_ORGANIZATION = randomOrganizationId();
  private static final URI OUR_SUB_ORGANIZATION = randomOrganizationId();
  private static final URI OTHER_ORGANIZATION = randomOrganizationId();

  @BeforeAll
  static void beforeAll() {
    CONTAINER.start();
  }

  @AfterAll
  static void afterAll() {
    CONTAINER.stop();
  }

  @BeforeEach
  void beforeEach() {
    CONTAINER.createIndex();

    username = "Curator from our organization";
    userTopLevelOrg = OUR_ORGANIZATION;
    userAccessRight = AccessRight.MANAGE_NVI_CANDIDATES;
    queryYear = String.valueOf(CURRENT_YEAR);

    handler =
        new FetchInstitutionStatusAggregationHandler(CONTAINER.getOpenSearchClient(), ENVIRONMENT);
    output = new ByteArrayOutputStream();
  }

  @AfterEach
  void afterEach() {
    CONTAINER.deleteIndex();
  }

  @Test
  void shouldReturnUnauthorizedWhenUserDoesNotHaveRequiredAccessRight() {
    userAccessRight = AccessRight.MANAGE_OWN_RESOURCES;
    var response = handleRequestExpectingProblem();
    assertThat(response)
        .extracting(Problem::getStatus)
        .extracting(StatusType::getStatusCode)
        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
  }

  @Test
  void shouldReturnOKWhenUserHasRequiredAccessRight() {
    var response = handleRequest();
    assertThat(response.getStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
  }

  @Test
  void shouldReturnEmptyResponseForOrganizationWithNoData() {
    userTopLevelOrg = OTHER_ORGANIZATION;
    var response = handleRequest();
    assertThat(response.getBody()).isEqualTo("{ }");
  }

  @Test
  void shouldReturnApprovalStatusAggregationForTopLevelOrganization() {
    var ourDocuments =
        createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_ORGANIZATION);
    var unrelatedDocument = createRandomIndexDocument(randomOrganizationId(), CURRENT_YEAR);
    var documentFromLastYear = createRandomIndexDocument(OUR_ORGANIZATION, CURRENT_YEAR - 1);
    CONTAINER.addDocumentsToIndex(ourDocuments);
    CONTAINER.addDocumentsToIndex(unrelatedDocument, documentFromLastYear);

    var expectedTotalPoints =
        getExpectedTotalPointsForTopLevelOrganization(OUR_ORGANIZATION, ourDocuments);

    var expectedResponse =
        stringFromResources(Path.of("institution_report.template"))
            .replace("__TOP_LEVEL_ORGANIZATION_ID__", OUR_ORGANIZATION.toString())
            .replace("__TOP_LEVEL_ORGANIZATION_POINTS__", expectedTotalPoints.toString());

    var response = handleRequest();

    assertEqualJsonContent(expectedResponse, response);
  }

  @Test
  void shouldIncludeSubOrganizationInApprovalStatusAggregation() {
    var documentsForTopLevelOrganization =
        createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_ORGANIZATION);
    var documentsForSubOrganization =
        createIndexDocumentsForAllApprovalStatusTypes(OUR_ORGANIZATION, OUR_SUB_ORGANIZATION);

    var allDocuments =
        Stream.of(documentsForTopLevelOrganization, documentsForSubOrganization)
            .flatMap(List::stream)
            .toList();
    CONTAINER.addDocumentsToIndex(allDocuments);

    var expectedTotalPoints =
        getExpectedTotalPointsForTopLevelOrganization(OUR_ORGANIZATION, allDocuments);

    // FIXME: Asserting current behavior and not the correct behavior, see NP-50248
    var expectedSubOrganizationPoints =
        getExpectedTotalPointsForTopLevelOrganization(
            OUR_ORGANIZATION, documentsForSubOrganization);

    var expectedResponse =
        stringFromResources(Path.of("institution_report_with_sub_organization.template"))
            .replace("__TOP_LEVEL_ORGANIZATION_ID__", OUR_ORGANIZATION.toString())
            .replace("__TOP_LEVEL_ORGANIZATION_POINTS__", expectedTotalPoints.toString())
            .replace("__SUB_ORGANIZATION_ID__", OUR_SUB_ORGANIZATION.toString())
            .replace("__SUB_ORGANIZATION_POINTS__", expectedSubOrganizationPoints.toString());

    var response = handleRequest();

    assertEqualJsonContent(expectedResponse, response);
  }

  @Test
  void shouldIncludeDocumentWithCreatorsFromBothTopAndSubOrganization() {
    var pointsPerCreator = randomBigDecimal();
    var expectedTotalPoints = pointsPerCreator.multiply(BigDecimal.TWO);

    var approval =
        new ApprovalFactory(OUR_ORGANIZATION)
            .withCreatorAffiliation(OUR_ORGANIZATION, pointsPerCreator)
            .withCreatorAffiliation(OUR_SUB_ORGANIZATION, pointsPerCreator)
            .build();
    var document = documentWithApprovals(approval, randomApproval());
    CONTAINER.addDocumentsToIndex(document);

    // FIXME: Asserting current behavior and not the correct behavior, see NP-50248
    var expectedResponse =
        stringFromResources(Path.of("institution_report_split_points.template"))
            .replace("__TOP_LEVEL_ORGANIZATION_ID__", OUR_ORGANIZATION.toString())
            .replace("__TOP_LEVEL_ORGANIZATION_POINTS__", expectedTotalPoints.toString())
            .replace("__SUB_ORGANIZATION_ID__", OUR_SUB_ORGANIZATION.toString())
            .replace("__SUB_ORGANIZATION_POINTS__", expectedTotalPoints.toString());

    var response = handleRequest();

    assertEqualJsonContent(expectedResponse, response);
  }

  private static void assertEqualJsonContent(
      String expectedResponse, GatewayResponse<String> response) {
    try {
      JSONAssert.assertEquals(expectedResponse, response.getBody(), getComparatorForPointValue());
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private BigDecimal getExpectedTotalPointsForTopLevelOrganization(
      URI organization, List<NviCandidateIndexDocument> ourDocuments) {
    return ourDocuments.stream()
        .map(NviCandidateIndexDocument::approvals)
        .flatMap(List::stream)
        .filter(approval -> organization.equals(approval.institutionId()))
        .filter(not(approval -> ApprovalStatus.REJECTED.equals(approval.approvalStatus())))
        .map(Approval::points)
        .map(InstitutionPoints::institutionPoints)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static CustomComparator getComparatorForPointValue() {
    return new CustomComparator(
        JSONCompareMode.STRICT,
        new Customization(
            "**.value", (o1, o2) -> hasEqualValue(toBigDecimal(o1), toBigDecimal(o2))));
  }

  private List<NviCandidateIndexDocument> createIndexDocumentsForAllApprovalStatusTypes(
      URI topLevelOrganization, URI creatorAffiliation) {
    var documents = new ArrayList<NviCandidateIndexDocument>();
    var approvalFactory = new ApprovalFactory(topLevelOrganization);
    for (var status : ApprovalStatus.values()) {
      var approval =
          approvalFactory
              .copy()
              .withApprovalStatus(status)
              .withCreatorAffiliation(creatorAffiliation)
              .build();
      documents.add(documentWithApprovals(approval));
    }
    return documents;
  }

  private static NviCandidateIndexDocument documentWithApprovals(Approval... approvals) {
    var allApprovals = List.of(approvals);
    var topLevelOrganizations = allApprovals.stream().map(Approval::institutionId).toList();
    var details = randomPublicationDetailsBuilder(topLevelOrganizations).build();
    return randomIndexDocumentBuilder(details, List.of(approvals)).build();
  }

  private GatewayResponse<String> handleRequest() {
    try {
      var request = createRequest();
      handler.handleRequest(request, output, CONTEXT);
      return GatewayResponse.fromOutputStream(output, String.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Problem handleRequestExpectingProblem() {
    try {
      var request = createRequest();
      handler.handleRequest(request, output, CONTEXT);
      var response = GatewayResponse.fromOutputStream(output, Problem.class);
      return objectMapper.readValue(response.getBody(), Problem.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream createRequest() {
    try {
      return new HandlerRequestBuilder<InputStream>(JsonUtils.dtoObjectMapper)
          .withTopLevelCristinOrgId(userTopLevelOrg)
          .withAccessRights(userTopLevelOrg, userAccessRight)
          .withUserName(username)
          .withPathParameters(Map.of(YEAR, queryYear))
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
