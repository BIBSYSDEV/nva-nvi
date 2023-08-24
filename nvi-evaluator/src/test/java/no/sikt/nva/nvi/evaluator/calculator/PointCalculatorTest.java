package no.sikt.nva.nvi.evaluator.calculator;

import static no.sikt.nva.nvi.evaluator.calculator.PointCalculator.calculatePoints;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
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
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PointCalculatorTest {

    @ParameterizedTest
    @MethodSource("singleCreatorSingleInstitutionPointProvider")
    void shouldCalculateNviPointsCorrectlyForSingleInstitution(PointParameters parameters) {

        var institutionId = randomUri();
        var expandedResource = createExpandedResource(
            randomUri(),
            createContributorNodes(getContributorNode(randomUri(), true, List.of(institutionId))),
            getInstanceTypeReference(parameters));

        var institutionPoints = calculatePoints(expandedResource, Set.of(institutionId));

        assertThat(institutionPoints.get(institutionId), is(equalTo(parameters.institution1Points())));
    }

    @ParameterizedTest
    @MethodSource("pointParametersAndResultProvider")
    void shouldCalculateNviPointsCorrectlyForCoPublishing(PointParameters parameters) {
        var nviInstitution1 = randomUri();
        var nviInstitution2 = randomUri();

        var creator1 = randomUri();
        var creator2 = randomUri();
        var expandedResource = createExpandedResource(
            randomUri(),
            createContributorNodes(
                getContributorNode(creator1, true, List.of(nviInstitution1, nviInstitution2)),
                getContributorNode(creator2, true, List.of(nviInstitution1)),
                getContributorNode(randomUri(), false, createRandomInstitutions(parameters))
            ),
            getInstanceTypeReference(parameters));

        var pointsMap = calculatePoints(expandedResource, Set.of(nviInstitution1, nviInstitution2));

        assertThat(pointsMap.get(nviInstitution1), is(equalTo(parameters.institution1Points())));
        assertThat(pointsMap.get(nviInstitution2), is(equalTo(parameters.institution2Points())));
    }

    private static Stream<PointParameters> pointParametersAndResultProvider() {
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
        var reference = objectMapper.createObjectNode().put("type", "Reference");
        reference.set("publicationInstance", objectMapper.createObjectNode().put("type", instanceType));
        var anthologyReference = createReference("Anthology", channelType, level);
        var entityDescription = objectMapper.createObjectNode();
        entityDescription.set("reference", anthologyReference);
        var publicationContext = objectMapper.createObjectNode();
        publicationContext.set("entityDescription", entityDescription);
        reference.set("publicationContext", publicationContext);
        return reference;
    }

    private static ObjectNode createReference(String instanceType, String channelType, String level) {
        var publicationContext = objectMapper.createObjectNode();
        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", instanceType);
        publicationContext.set(channelType.toLowerCase(), objectMapper.createObjectNode()
                                                              .put("id", randomUri().toString())
                                                              .put("type", channelType)
                                                              .put("level", level));
        var reference = objectMapper.createObjectNode();
        reference.set("publicationContext", publicationContext);
        reference.set("publicationInstance", publicationInstance);

        return reference;
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val).setScale(4, RoundingMode.HALF_UP);
    }

    private static List<URI> createRandomInstitutions(PointParameters parameters) {
        return IntStream.range(0, parameters.creatorShareCount() - 3).boxed().map(e -> randomUri()).toList();
    }

    private static JsonNode createContributorNodes(JsonNode... contributorNode) {
        return objectMapper.createArrayNode().addAll(Arrays.stream(contributorNode).toList());
    }

    private static JsonNode createJournalReference(String instanceType, String level) {
        var reference = objectMapper.createObjectNode();

        var publicationInstance = objectMapper.createObjectNode();
        publicationInstance.put("type", instanceType);
        reference.set("publicationInstance", publicationInstance);

        var publicationContext = objectMapper.createObjectNode();
        publicationContext.put("id", randomUri().toString());
        publicationContext.put("type", "Journal");
        publicationContext.put("level", level);
        reference.set("publicationContext", publicationContext);

        return reference;
    }

    private static ObjectNode getContributorNode(URI contributor, boolean isVerified, List<URI> affiliations) {
        var contributorNode = objectMapper.createObjectNode();

        contributorNode.put("type", "Contributor");

        var affiliationsNode = objectMapper.createArrayNode();

        affiliations.stream()
            .map(PointCalculatorTest::createAffiliationNode)
            .forEach(affiliationsNode::add);

        contributorNode.set("affiliations", affiliationsNode);

        contributorNode.set("identity", createIdentity(contributor, isVerified));
        return contributorNode;
    }

    private static ObjectNode createIdentity(URI contributor, boolean isVerified) {
        return objectMapper.createObjectNode()
                   .put("id", contributor.toString())
                   .put("verificationStatus", isVerified ? "Verified" : "NonVerified")
                   .put("type", "Identity");
    }

    private static ObjectNode createAffiliationNode(URI affiliation) {
        return objectMapper.createObjectNode()
                   .put("id", affiliation.toString())
                   .put("type", "Organization");
    }

    private static JsonNode createExpandedResource(URI publicationId, JsonNode contributors,
                                                   JsonNode reference) {
        var root = objectMapper.createObjectNode();

        root.put("id", publicationId.toString());

        var entityDescription = objectMapper.createObjectNode();

        entityDescription.set("contributors", contributors);

        entityDescription.set("reference", reference);

        root.set("entityDescription", entityDescription);

        return root;
    }

    private record PointParameters(String instanceType, String channelType, String level,
                                   boolean isInternationalCollaboration,
                                   int creatorShareCount, BigDecimal institution1Points,
                                   BigDecimal institution2Points
    ) {

    }
}