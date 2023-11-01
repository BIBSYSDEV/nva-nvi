package no.sikt.nva.nvi.evaluator.calculator;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.evaluator.calculator.PointCalculator.calculatePoints;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PointCalculatorTest {

    private static final String COUNTRY_CODE_NO = "NO";
    private static final String SOME_INTERNATIONAL_COUNTRY_CODE = Locale.UK.getCountry();
    private static final String ROLE = "role";
    private static final String ROLE_CREATOR = "Creator";
    private static final String SOME_OTHER_ROLE = "SomeOtherRole";
    private static final String TYPE = "type";
    private static final String PUBLICATION_INSTANCE = "publicationInstance";
    private static final String REFERENCE = "reference";
    private static final String ENTITY_DESCRIPTION = "entityDescription";
    private static final String PUBLICATION_CONTEXT = "publicationContext";
    private static final String ID = "id";
    private static final String LEVEL = "level";
    private static final JsonNode HARDCODED_JOURNAL_REFERENCE = createJournalReference("AcademicArticle", "1");
    private static final String AFFILIATIONS = "affiliations";
    private static final String IDENTITY = "identity";
    private static final String VERIFICATION_STATUS = "verificationStatus";
    private static final String CONTRIBUTORS = "contributors";

    @ParameterizedTest(name = "Should calculate points correctly single contributor affiliated with a single "
                              + "institution. No international collaboration.")
    @MethodSource("singleCreatorSingleInstitutionPointProvider")
    void shouldCalculateNviPointsForSingleInstitution(PointParameters parameters) {

        var creatorId = randomUri();
        var institutionId = randomUri();
        var expandedResource = setUpSingleContributorWithSingleInstitution(parameters, creatorId, institutionId);

        var pointCalculation = calculatePoints(expandedResource, Map.of(creatorId, List.of(institutionId)));

        assertThat(pointCalculation.institutionPoints().get(institutionId),
                   is(equalTo(parameters.institution1Points())));
        assertThat(pointCalculation.totalPoints(), is(equalTo(parameters.totalPoints())));
    }

    @ParameterizedTest(name = "Should calculate points correctly for co-publishing cases where two creators are "
                              + "affiliated with one of the two institutions involved.")
    @MethodSource("twoCreatorsAffiliatedWithOneInstitutionPointProvider")
    void shouldCalculateNviPointsForCoPublishingTwoCreatorsAffiliatedWithOneInstitution(PointParameters parameters) {
        var nviInstitution1 = randomUri();
        var nviInstitution2 = randomUri();
        var creator1 = randomUri();
        var creator2 = randomUri();
        var creator1Institutions = List.of(nviInstitution1, nviInstitution2);
        var creator2Institutions = List.of(nviInstitution1);
        var expandedResource = createExpandedResourceWithManyCreators(parameters, creator1, creator2,
                                                                      creator1Institutions,
                                                                      creator2Institutions);

        var pointCalculation = calculatePoints(expandedResource, Map.of(creator1, creator1Institutions, creator2,
                                                                        creator2Institutions));

        assertThat(pointCalculation.institutionPoints().get(nviInstitution1),
                   is(equalTo(parameters.institution1Points())));
        assertThat(pointCalculation.institutionPoints().get(nviInstitution2),
                   is(equalTo(parameters.institution2Points())));
        assertThat(pointCalculation.totalPoints(), is(equalTo(parameters.totalPoints())));
    }

    @ParameterizedTest(name = "Should calculate points correctly for co-publishing cases where two creators are "
                              + "affiliated with two different institutions.")
    @MethodSource("twoCreatorsAffiliatedWithTwoDifferentInstitutionsPointProvider")
    void shouldCalculateNviPointsForCoPublishingTwoCreatorsTwoInstitutions(PointParameters parameters) {
        var nviInstitution1 = randomUri();
        var nviInstitution2 = randomUri();

        var creator1 = randomUri();
        var creator2 = randomUri();
        var creator1Institutions = List.of(nviInstitution1);
        var creator2Institutions = List.of(nviInstitution2);
        var expandedResource = setUpCoPublishingTwoCreatorsTwoInstitutions(parameters, creator1, creator2,
                                                                           creator1Institutions);

        var pointCalculation = calculatePoints(expandedResource, Map.of(creator1, creator1Institutions, creator2,
                                                                        creator2Institutions));

        assertThat(pointCalculation.institutionPoints().get(nviInstitution1),
                   is(equalTo(parameters.institution1Points())));
        assertThat(pointCalculation.institutionPoints().get(nviInstitution2),
                   is(equalTo(parameters.institution2Points())));
        assertThat(pointCalculation.totalPoints(), is(equalTo(parameters.totalPoints())));
    }

    @Test
    void shouldNotGiveInternationalPointsIfNonCreatorAffiliatedWithInternationalInstitution() {
        var creatorId = randomUri();
        var institutionId = randomUri();
        var expandedResource = setUpNonCreatorAffiliatedWithInternationalInstitution(creatorId, institutionId);

        var institutionPoints = calculatePoints(expandedResource,
                                                Map.of(creatorId, List.of(institutionId))).institutionPoints();

        assertThat(institutionPoints.get(institutionId), is(equalTo(asBigDecimal("1"))));
    }

    @Test
    void shouldNotCountCreatorSharesForNonCreators() {
        var creatorId = randomUri();
        var institutionId = randomUri();
        var expandedResource = setUpResourceWithNonCreator(creatorId, institutionId);

        var institutionPoints = calculatePoints(expandedResource,
                                                Map.of(creatorId, List.of(institutionId))).institutionPoints();

        assertThat(institutionPoints.get(institutionId), is(equalTo(asBigDecimal("1"))));
    }

    @Test
    void shouldCountOneCreatorShareForCreatorsWithoutAffiliations() {
        var nviCreatorId = randomUri();
        var nviInstitutionId = randomUri();
        var expandedResource = setUpResourceWithCreatorsWithoutAffiliations(nviCreatorId, nviInstitutionId);
        var institutionPoints = calculatePoints(expandedResource,
                                                Map.of(nviCreatorId, List.of(nviInstitutionId))).institutionPoints();

        assertThat(institutionPoints.get(nviInstitutionId), is(equalTo(asBigDecimal("0.7071"))));
    }

    @Test
    void shouldNotCountCreatorSharesForAffiliationsWithoutId() {
        var nviCreatorId = randomUri();
        var nviInstitutionId = randomUri();
        int numberOfAffiliationsWithoutId = randomIntBetween(2, 10);
        var expandedResource = setUpResourceWithCreatorWithUnverifiedAffiliations(nviCreatorId, nviInstitutionId,
                                                                                  numberOfAffiliationsWithoutId);

        var institutionPoints = calculatePoints(expandedResource,
                                                Map.of(nviCreatorId, List.of(nviInstitutionId))).institutionPoints();

        assertThat(institutionPoints.get(nviInstitutionId), is(equalTo(asBigDecimal("1"))));
    }

    @Test
    void shouldCountOneCreatorShareForCreatorsWithOnlyAffiliationsWithoutId() {
        var nviCreatorId = randomUri();
        var nviInstitutionId = randomUri();
        int numberOfEmptyAffiliationsWithoutId = randomIntBetween(2, 10);
        var expandedResource = setUpResourceWithOneCreatorWithUnverifiedAffiliations(nviCreatorId, nviInstitutionId,
                                                                                     numberOfEmptyAffiliationsWithoutId);

        var institutionPoints = calculatePoints(expandedResource,
                                                Map.of(nviCreatorId, List.of(nviInstitutionId))).institutionPoints();

        assertThat(institutionPoints.get(nviInstitutionId), is(equalTo(asBigDecimal("0.7071"))));
    }

    private static JsonNode setUpResourceWithOneCreatorWithUnverifiedAffiliations(URI nviCreatorId,
                                                                                  URI nviInstitutionId,
                                                                                  int numberOfEmptyAffiliationsWithoutId) {
        return createExpandedResourceWithTwoVerifiedContributors(nviCreatorId,
                                                                 ROLE_CREATOR,
                                                                 Map.of(nviInstitutionId,
                                                                        COUNTRY_CODE_NO),
                                                                 randomUri(),
                                                                 ROLE_CREATOR,
                                                                 emptyMap(),
                                                                 HARDCODED_JOURNAL_REFERENCE,
                                                                 numberOfEmptyAffiliationsWithoutId);
    }

    private static JsonNode setUpResourceWithCreatorWithUnverifiedAffiliations(URI nviCreatorId, URI nviInstitutionId,
                                                                               int numberOfEmptyAffiliationsWithoutId) {
        return createExpandedResource(randomUri(),
                                      createContributorNodes(
                                          createContributorNode(nviCreatorId, true,
                                                                Map.of(nviInstitutionId,
                                                                       COUNTRY_CODE_NO),
                                                                ROLE_CREATOR,
                                                                numberOfEmptyAffiliationsWithoutId)),
                                      HARDCODED_JOURNAL_REFERENCE);
    }

    private static JsonNode setUpResourceWithCreatorsWithoutAffiliations(URI nviCreatorId, URI nviInstitutionId) {
        return createExpandedResourceWithTwoVerifiedContributors(nviCreatorId,
                                                                 ROLE_CREATOR,
                                                                 Map.of(nviInstitutionId, COUNTRY_CODE_NO),
                                                                 randomUri(),
                                                                 ROLE_CREATOR,
                                                                 Collections.emptyMap(),
                                                                 HARDCODED_JOURNAL_REFERENCE,
                                                                 0);
    }

    private static JsonNode setUpResourceWithNonCreator(URI creatorId, URI institutionId) {
        return createExpandedResourceWithTwoVerifiedContributors(creatorId,
                                                                 ROLE_CREATOR,
                                                                 Map.of(institutionId, COUNTRY_CODE_NO),
                                                                 randomUri(),
                                                                 SOME_OTHER_ROLE,
                                                                 Map.of(randomUri(),
                                                                        COUNTRY_CODE_NO),
                                                                 HARDCODED_JOURNAL_REFERENCE, 0);
    }

    private static JsonNode setUpNonCreatorAffiliatedWithInternationalInstitution(URI creatorId, URI institutionId) {
        return createExpandedResourceWithTwoVerifiedContributors(creatorId,
                                                                 ROLE_CREATOR,
                                                                 Map.of(institutionId, COUNTRY_CODE_NO),
                                                                 randomUri(),
                                                                 SOME_OTHER_ROLE,
                                                                 Map.of(randomUri(),
                                                                        SOME_INTERNATIONAL_COUNTRY_CODE),
                                                                 HARDCODED_JOURNAL_REFERENCE, 0
        );
    }

    private static JsonNode setUpCoPublishingTwoCreatorsTwoInstitutions(PointParameters parameters, URI creator1,
                                                                        URI creator2,
                                                                        List<URI> creator1Institutions) {
        return createExpandedResourceWithTwoVerifiedContributors(creator1, ROLE_CREATOR,
                                                                 toMapWithCountryCode(creator1Institutions,
                                                                                      COUNTRY_CODE_NO),
                                                                 creator2,
                                                                 ROLE_CREATOR,
                                                                 toMapWithCountryCode(
                                                                     creator1Institutions,
                                                                     COUNTRY_CODE_NO),
                                                                 getInstanceTypeReference(parameters),
                                                                 0);
    }

    private static JsonNode setUpSingleContributorWithSingleInstitution(PointParameters parameters, URI creator,
                                                                        URI institutionId) {
        return createExpandedResource(
            randomUri(), createContributorNodes(
                createContributorNode(creator, true, Map.of(institutionId, COUNTRY_CODE_NO), ROLE_CREATOR, 0)),
            getInstanceTypeReference(parameters));
    }

    private static JsonNode createExpandedResourceWithManyCreators(PointParameters parameters, URI creator1,
                                                                   URI creator2,
                                                                   List<URI> creator1Institutions,
                                                                   List<URI> creator2Institutions) {
        var countryCodeForNonNviCreators = parameters.isInternationalCollaboration()
                                               ? SOME_INTERNATIONAL_COUNTRY_CODE : COUNTRY_CODE_NO;
        var contributorNodes = createContributorNodes(
            createContributorNode(creator1, true, toMapWithCountryCode(creator1Institutions, COUNTRY_CODE_NO),
                                  ROLE_CREATOR, 0),
            createContributorNode(creator2, true, toMapWithCountryCode(creator2Institutions, COUNTRY_CODE_NO),
                                  ROLE_CREATOR, 0),
            createContributorNode(randomUri(), false,
                                  toMapWithCountryCode(createRandomInstitutions(parameters, 3),
                                                       countryCodeForNonNviCreators),
                                  parameters.creatorShareCount() == 3 ? SOME_OTHER_ROLE : ROLE_CREATOR, 0)
        );
        return createExpandedResource(randomUri(), contributorNodes, getInstanceTypeReference(parameters));
    }

    private static JsonNode createExpandedResourceWithTwoVerifiedContributors(URI creator1,
                                                                              String contributor1Role,
                                                                              Map<URI, String> creator1Institution,
                                                                              URI creator2,
                                                                              String contributor2Role,
                                                                              Map<URI, String> creator2Institution,
                                                                              JsonNode reference,
                                                                              int creator2numberOfEmptyAffiliations) {
        return createExpandedResource(randomUri(),
                                      createContributorNodes(
                                          createContributorNode(creator1, true,
                                                                creator1Institution,
                                                                contributor1Role, 0),
                                          createContributorNode(creator2, true, creator2Institution,
                                                                contributor2Role, creator2numberOfEmptyAffiliations)),
                                      reference);
    }

    private static Map<URI, String> toMapWithCountryCode(List<URI> creator1Institutions, String countryCode) {
        return creator1Institutions.stream()
                   .collect(Collectors.toMap(institutionId -> institutionId,
                                             institutionId -> countryCode));
    }

    private static Stream<PointParameters> twoCreatorsAffiliatedWithTwoDifferentInstitutionsPointProvider() {
        return Stream.of(
            //example from cristin calculations, publicationYear 2022
            new PointParameters("AcademicArticle", "Journal", "1", false, 2, asBigDecimal("0.7071"),
                                asBigDecimal("0.7071"), asBigDecimal("1.4142"))
        );
    }

    private static Stream<PointParameters> twoCreatorsAffiliatedWithOneInstitutionPointProvider() {
        return Stream.of(
            new PointParameters("AcademicMonograph", "Series", "1", false, 3, asBigDecimal("4.0825"),
                                asBigDecimal("2.8868"), asBigDecimal("8.6603")),
            new PointParameters("AcademicMonograph", "Series", "2", true, 4, asBigDecimal("7.3539"),
                                asBigDecimal("5.2000"), asBigDecimal("20.8000")),
            new PointParameters("AcademicMonograph", "Series", "2", false, 4, asBigDecimal("5.6569"),
                                asBigDecimal("4.0000"), asBigDecimal("16")),
            new PointParameters("AcademicChapter", "Series", "1", true, 5, asBigDecimal("0.8222"),
                                asBigDecimal("0.5814"), asBigDecimal("2.9069")),
            new PointParameters("AcademicChapter", "Series", "2", false, 5, asBigDecimal("1.8974"),
                                asBigDecimal("1.3416"), asBigDecimal("6.7082")),
            new PointParameters("AcademicArticle", "Journal", "1", false, 7, asBigDecimal("0.5345"),
                                asBigDecimal("0.3780"), asBigDecimal("2.6458")),
            new PointParameters("AcademicArticle", "Journal", "2", false, 7, asBigDecimal("1.6036"),
                                asBigDecimal("1.1339"), asBigDecimal("7.9373")),
            new PointParameters("AcademicLiteratureReview", "Journal", "1", true, 8, asBigDecimal("0.6500"),
                                asBigDecimal("0.4596"), asBigDecimal("3.6770")),
            new PointParameters("AcademicLiteratureReview", "Journal", "2", true, 8, asBigDecimal("1.9500"),
                                asBigDecimal("1.3789"), asBigDecimal("11.0309"))
        );
    }

    private static Stream<PointParameters> singleCreatorSingleInstitutionPointProvider() {
        return Stream.of(
            new PointParameters("AcademicMonograph", "Series", "1", false, 1, asBigDecimal("5"), null,
                                asBigDecimal("5")),
            new PointParameters("AcademicMonograph", "Series", "2", false, 1, asBigDecimal("8"), null,
                                asBigDecimal("8")),
            new PointParameters("AcademicMonograph", "Publisher", "1", false, 1, asBigDecimal("5"), null,
                                asBigDecimal("5")),
            new PointParameters("AcademicMonograph", "Publisher", "2", false, 1, asBigDecimal("8"), null,
                                asBigDecimal("8")),
            new PointParameters("AcademicChapter", "Series", "1", false, 1, asBigDecimal("1"), null, asBigDecimal("1")),
            new PointParameters("AcademicChapter", "Series", "2", false, 1, asBigDecimal("3"), null, asBigDecimal("3")),
            new PointParameters("AcademicChapter", "Publisher", "1", false, 1, asBigDecimal("0.7"), null,
                                asBigDecimal("0.7")),
            new PointParameters("AcademicChapter", "Publisher", "2", false, 1, asBigDecimal("1"), null,
                                asBigDecimal("1")),
            new PointParameters("AcademicArticle", "Journal", "1", false, 1, asBigDecimal("1"), null,
                                asBigDecimal("1")),
            new PointParameters("AcademicArticle", "Journal", "2", false, 1, asBigDecimal("3"), null,
                                asBigDecimal("3")),
            new PointParameters("AcademicLiteratureReview", "Journal", "1", false, 1, asBigDecimal("1"), null,
                                asBigDecimal("1")),
            new PointParameters("AcademicLiteratureReview", "Journal", "2", false, 1, asBigDecimal("3"), null,
                                asBigDecimal("3"))
        );
    }

    private static JsonNode getInstanceTypeReference(PointParameters parameters) {
        return switch (parameters.instanceType()) {
            case "AcademicArticle", "AcademicLiteratureReview" ->
                createJournalReference(parameters.instanceType(), parameters.level());
            case "AcademicMonograph" ->
                createReference(parameters.instanceType(), parameters.channelType(), parameters.level());
            case "AcademicChapter" -> createChapterReference(parameters.instanceType(),
                                                             parameters.channelType(), parameters.level());
            default -> objectMapper.createObjectNode();
        };
    }

    private static JsonNode createChapterReference(String instanceType, String channelType, String level) {
        var reference = objectMapper.createObjectNode().put(TYPE, "Reference");
        reference.set(PUBLICATION_INSTANCE, objectMapper.createObjectNode().put(TYPE, instanceType));
        var anthologyReference = createReference("Anthology", channelType, level);
        var entityDescription = objectMapper.createObjectNode();
        entityDescription.set(REFERENCE, anthologyReference);
        var publicationContext = objectMapper.createObjectNode();
        publicationContext.set(ENTITY_DESCRIPTION, entityDescription);
        reference.set(PUBLICATION_CONTEXT, publicationContext);
        return reference;
    }

    private static ObjectNode createReference(String instanceType, String channelType, String level) {
        var publicationContext = objectMapper.createObjectNode();
        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put(TYPE, instanceType);
        publicationContext.set(channelType.toLowerCase(), objectMapper.createObjectNode()
                                                              .put(ID, randomUri().toString())
                                                              .put(TYPE, channelType)
                                                              .put(LEVEL, level));
        var reference = objectMapper.createObjectNode();
        reference.set(PUBLICATION_CONTEXT, publicationContext);
        reference.set(PUBLICATION_INSTANCE, publicationInstance);

        return reference;
    }

    private static BigDecimal asBigDecimal(String val) {
        return new BigDecimal(val).setScale(4, RoundingMode.HALF_UP);
    }

    private static List<URI> createRandomInstitutions(PointParameters parameters, int contributorTestShareCount) {
        return IntStream.range(0, parameters.creatorShareCount() - contributorTestShareCount)
                   .boxed()
                   .map(e -> randomUri())
                   .toList();
    }

    private static JsonNode createContributorNodes(JsonNode... contributorNode) {
        return objectMapper.createArrayNode().addAll(Arrays.stream(contributorNode).toList());
    }

    private static JsonNode createJournalReference(String instanceType, String level) {
        var reference = objectMapper.createObjectNode();

        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put(TYPE, instanceType);
        reference.set(PUBLICATION_INSTANCE, publicationInstance);

        var publicationContext = objectMapper.createObjectNode();
        publicationContext.put(ID, randomUri().toString());
        publicationContext.put(TYPE, "Journal");
        publicationContext.put(LEVEL, level);
        reference.set(PUBLICATION_CONTEXT, publicationContext);

        return reference;
    }

    private static ObjectNode createContributorNode(URI contributor, boolean isVerified,
                                                    Map<URI, String> affiliationsWithCountryCode, String role,
                                                    int numberOfAffiliationsWithoutId) {
        var contributorNode = objectMapper.createObjectNode();

        contributorNode.put(TYPE, "Contributor");

        var affiliationsNode = createAffiliationsNode(affiliationsWithCountryCode, numberOfAffiliationsWithoutId);

        var roleNode = objectMapper.createObjectNode().put(TYPE, role);

        contributorNode.set(ROLE, roleNode);

        contributorNode.set(AFFILIATIONS, affiliationsNode);

        contributorNode.set(IDENTITY, createIdentity(contributor, isVerified));
        return contributorNode;
    }

    private static ArrayNode createAffiliationsNode(Map<URI, String> affiliationsWithCountryCode,
                                                    int numberOfAffiliationsWithoutId) {
        var affiliationsNode = objectMapper.createArrayNode();

        affiliationsWithCountryCode.entrySet().stream()
            .map(entry -> createAffiliationNode(entry.getKey(), entry.getValue()))
            .forEach(affiliationsNode::add);

        for (int i = 1; i <= numberOfAffiliationsWithoutId; i++) {
            affiliationsNode.add(createAffiliationNodeWithoutId());
            i++;
        }

        return affiliationsNode;
    }

    private static ObjectNode createIdentity(URI contributor, boolean isVerified) {
        return objectMapper.createObjectNode()
                   .put(ID, contributor.toString())
                   .put(VERIFICATION_STATUS, isVerified ? "Verified" : "NonVerified")
                   .put(TYPE, "Identity");
    }

    private static ObjectNode createAffiliationNode(URI affiliationId, String countryCode) {
        return objectMapper.createObjectNode()
                   .put(ID, affiliationId.toString())
                   .put(TYPE, "Organization")
                   .put("countryCode", countryCode);
    }

    private static ObjectNode createAffiliationNodeWithoutId() {
        return objectMapper.createObjectNode()
                   .put(TYPE, "Organization");
    }

    private static JsonNode createExpandedResource(URI publicationId, JsonNode contributors, JsonNode reference) {
        var root = objectMapper.createObjectNode();

        root.put(ID, publicationId.toString());

        var entityDescription = objectMapper.createObjectNode();

        entityDescription.set(CONTRIBUTORS, contributors);

        entityDescription.set(REFERENCE, reference);

        root.set(ENTITY_DESCRIPTION, entityDescription);

        return root;
    }

    private record PointParameters(String instanceType, String channelType, String level,
                                   boolean isInternationalCollaboration,
                                   int creatorShareCount, BigDecimal institution1Points,
                                   BigDecimal institution2Points, BigDecimal totalPoints
    ) {

    }
}