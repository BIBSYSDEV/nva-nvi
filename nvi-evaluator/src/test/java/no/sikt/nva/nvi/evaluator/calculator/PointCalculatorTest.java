package no.sikt.nva.nvi.evaluator.calculator;

import static no.sikt.nva.nvi.evaluator.calculator.PointCalculator.calculatePoints;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PointCalculatorTest {

    private static final String TYPE = "type";
    private static final String PUBLICATION_INSTANCE = "publicationInstance";
    private static final String REFERENCE = "reference";
    private static final String ENTITY_DESCRIPTION = "entityDescription";
    private static final String PUBLICATION_CONTEXT = "publicationContext";
    private static final String ID = "id";
    private static final String LEVEL = "level";
    private static final String AFFILIATIONS = "affiliations";
    private static final String IDENTITY = "identity";
    private static final String VERIFICATION_STATUS = "verificationStatus";
    private static final String CONTRIBUTORS = "contributors";

    @ParameterizedTest(name = "Should calculate points correctly single contributor affiliated with a single "
                              + "institution. No international collaboration.")
    @MethodSource("singleCreatorSingleInstitutionPointProvider")
    void shouldCalculateNviPointsForSingleInstitution(PointParameters parameters) {

        var creator = randomUri();
        var institutionId = randomUri();
        var institutions = List.of(institutionId);
        var expandedResource = createExpandedResource(
            randomUri(),
            createContributorNodes(getContributorNode(creator, true, institutions)),
            getInstanceTypeReference(parameters));

        var institutionPoints = calculatePoints(expandedResource, Map.of(creator, institutions));

        assertThat(institutionPoints.get(institutionId), is(equalTo(parameters.institution1Points())));
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
        var expandedResource = createExpandedResource(
            randomUri(),
            createContributorNodes(
                getContributorNode(creator1, true, creator1Institutions),
                getContributorNode(creator2, true, creator2Institutions),
                getContributorNode(randomUri(), false, createRandomInstitutions(parameters, 3))
            ),
            getInstanceTypeReference(parameters));

        var pointsMap = calculatePoints(expandedResource, Map.of(creator1, creator1Institutions,
                                                                 creator2, creator2Institutions));

        assertThat(pointsMap.get(nviInstitution1), is(equalTo(parameters.institution1Points())));
        assertThat(pointsMap.get(nviInstitution2), is(equalTo(parameters.institution2Points())));
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
        var expandedResource = createExpandedResource(
            randomUri(),
            createContributorNodes(
                getContributorNode(creator1, true, creator1Institutions),
                getContributorNode(creator2, true, creator2Institutions),
                getContributorNode(randomUri(), randomBoolean(), createRandomInstitutions(parameters, 2))
            ),
            getInstanceTypeReference(parameters));

        var pointsMap = calculatePoints(expandedResource,
                                        Map.of(creator1, creator1Institutions,
                                               creator2, creator2Institutions));

        assertThat(pointsMap.get(nviInstitution1), is(equalTo(parameters.institution1Points())));
        assertThat(pointsMap.get(nviInstitution2), is(equalTo(parameters.institution2Points())));
    }

    private static Stream<PointParameters> twoCreatorsAffiliatedWithTwoDifferentInstitutionsPointProvider() {
        return Stream.of(
            //example from cristin calculations, publicationYear 2022
            new PointParameters("AcademicArticle", "Journal", "1", false, 2, bd("0.7071"), bd("0.7071"))
        );
    }

    private static Stream<PointParameters> twoCreatorsAffiliatedWithOneInstitutionPointProvider() {
        //TODO Disabled isInternationCollaboration tests until parameter available
        return Stream.of(
            //new PointParameters("AcademicMonograph", "Series", "1", true, 3, bd("5.3072"), bd("3.7528")),
            new PointParameters("AcademicMonograph", "Series", "1", false, 3, bd("4.0825"), bd("2.8868")),
            //new PointParameters("AcademicMonograph", "Series", "2", true, 4, bd("7.3539"), bd("5.2000")),
            new PointParameters("AcademicMonograph", "Series", "2", false, 4, bd("5.6569"), bd("4.0000")),
            //new PointParameters("AcademicChapter", "Series", "1", true, 5, bd("0.6325"), bd("0.4472")),
            new PointParameters("AcademicChapter", "Series", "2", false, 5, bd("1.8974"), bd("1.3416")),
            new PointParameters("AcademicArticle", "Journal", "1", false, 7, bd("0.5345"), bd("0.3780")),
            new PointParameters("AcademicArticle", "Journal", "2", false, 7, bd("1.6036"), bd("1.1339"))
            //new PointParameters("AcademicLiteratureReview", "Journal", "1", true, 8, bd("0.6500"), bd("0.4596")),
            //new PointParameters("AcademicLiteratureReview", "Journal", "2", true, 8, bd("1.9500"), bd("1.3789"))
        );
    }

    private static Stream<PointParameters> singleCreatorSingleInstitutionPointProvider() {
        return Stream.of(
            new PointParameters("AcademicMonograph", "Series", "1", false, 1, bd("5"), null),
            new PointParameters("AcademicMonograph", "Series", "2", false, 1, bd("8"), null),
            new PointParameters("AcademicMonograph", "Publisher", "1", false, 1, bd("5"), null),
            new PointParameters("AcademicMonograph", "Publisher", "2", false, 1, bd("8"), null),
            new PointParameters("AcademicChapter", "Series", "1", false, 1, bd("1"), null),
            new PointParameters("AcademicChapter", "Series", "2", false, 1, bd("3"), null),
            new PointParameters("AcademicChapter", "Publisher", "1", false, 1, bd("0.7"), null),
            new PointParameters("AcademicChapter", "Publisher", "2", false, 1, bd("1"), null),
            new PointParameters("AcademicArticle", "Journal", "1", false, 1, bd("1"), null),
            new PointParameters("AcademicArticle", "Journal", "2", false, 1, bd("3"), null),
            new PointParameters("AcademicLiteratureReview", "Journal", "1", false, 1, bd("1"), null),
            new PointParameters("AcademicLiteratureReview", "Journal", "2", false, 1, bd("3"), null)
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

    private static BigDecimal bd(String val) {
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

    private static ObjectNode getContributorNode(URI contributor, boolean isVerified, List<URI> affiliations) {
        var contributorNode = objectMapper.createObjectNode();

        contributorNode.put(TYPE, "Contributor");

        var affiliationsNode = objectMapper.createArrayNode();

        affiliations.stream()
            .map(PointCalculatorTest::createAffiliationNode)
            .forEach(affiliationsNode::add);

        contributorNode.set(AFFILIATIONS, affiliationsNode);

        contributorNode.set(IDENTITY, createIdentity(contributor, isVerified));
        return contributorNode;
    }

    private static ObjectNode createIdentity(URI contributor, boolean isVerified) {
        return objectMapper.createObjectNode()
                   .put(ID, contributor.toString())
                   .put(VERIFICATION_STATUS, isVerified ? "Verified" : "NonVerified")
                   .put(TYPE, "Identity");
    }

    private static ObjectNode createAffiliationNode(URI affiliation) {
        return objectMapper.createObjectNode()
                   .put(ID, affiliation.toString())
                   .put(TYPE, "Organization");
    }

    private static JsonNode createExpandedResource(URI publicationId, JsonNode contributors,
                                                   JsonNode reference) {
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
                                   BigDecimal institution2Points
    ) {

    }
}