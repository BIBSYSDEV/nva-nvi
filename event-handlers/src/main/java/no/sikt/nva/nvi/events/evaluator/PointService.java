package no.sikt.nva.nvi.events.evaluator;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CHAPTER_SERIES_SCIENTIFIC_VALUE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_COUNTRY_CODE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ID;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_INSTANCE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLICATION_CONTEXT;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_PUBLISHER;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_ROLE_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_SERIES_SCIENTIFIC_VALUE;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_TYPE;
import static no.sikt.nva.nvi.common.utils.JsonUtils.extractJsonNodeTextValue;
import static no.sikt.nva.nvi.common.utils.JsonUtils.streamNode;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.Channel;
import no.sikt.nva.nvi.events.evaluator.model.PointCalculation;
import no.sikt.nva.nvi.events.evaluator.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.events.evaluator.model.VerifiedNviCreator;

public final class PointService {

  private static final String COUNTRY_CODE_NORWAY = "NO";
  private static final String ROLE_CREATOR = "Creator";
  private static final String TYPE = "type";
  private static final String TYPE_SERIES = "Series";
  private static final String TYPE_JOURNAL = "Journal";
  private static final String UNASSIGNED = "Unassigned";
  private final OrganizationRetriever organizationRetriever;

  public PointService(OrganizationRetriever organizationRetriever) {
    this.organizationRetriever = organizationRetriever;
  }

  public PointCalculation calculatePoints(
      JsonNode publication,
      Collection<VerifiedNviCreator> verifiedNviCreators,
      Collection<UnverifiedNviCreator> unverifiedNviCreators) {
    var instanceType = extractInstanceType(publication);
    massiveHackToFixObjectsWithMultipleTypes(publication);
    var channel = extractChannel(instanceType, publication);
    var isInternationalCollaboration = isInternationalCollaboration(publication);
    var creatorShareCount = countCreatorShares(publication);
    return new PointCalculator(
            channel,
            instanceType,
            verifiedNviCreators,
            unverifiedNviCreators,
            isInternationalCollaboration,
            creatorShareCount)
        .calculatePoints();
  }

  private static boolean isInternationalCollaboration(JsonNode jsonNode) {
    return getJsonNodeStream(jsonNode, JSON_PTR_CONTRIBUTOR)
        .filter(PointService::isCreator)
        .flatMap(PointService::extractAffiliations)
        .map(PointService::extractCountryCode)
        .filter(Objects::nonNull)
        .anyMatch(PointService::isInternationalCountryCode);
  }

  private static String extractCountryCode(JsonNode affiliationNode) {
    return extractJsonNodeTextValue(affiliationNode, JSON_PTR_COUNTRY_CODE);
  }

  private static Stream<JsonNode> extractAffiliations(JsonNode contributorNode) {
    return getJsonNodeStream(contributorNode, JSON_PTR_AFFILIATIONS);
  }

  private static boolean isCreator(JsonNode contributorNode) {
    return ROLE_CREATOR.equals(extractJsonNodeTextValue(contributorNode, JSON_PTR_ROLE_TYPE));
  }

  private static boolean isInternationalCountryCode(String countryCode) {
    return !COUNTRY_CODE_NORWAY.equals(countryCode);
  }

  private static Integer countCreatorsWithoutAffiliations(List<JsonNode> creators) {
    return creators.stream()
        .filter(PointService::doesNotHaveAffiliations)
        .map(node -> 1)
        .reduce(0, Integer::sum);
  }

  private static Integer countCreatorsWithOnlyUnverifiedAffiliations(List<JsonNode> creators) {
    return creators.stream()
        .filter(PointService::hasAffiliations)
        .filter(PointService::isOnlyAffiliatedWithOrganizationsWithOutId)
        .map(node -> 1)
        .reduce(0, Integer::sum);
  }

  private static boolean isOnlyAffiliatedWithOrganizationsWithOutId(JsonNode contributor) {
    return extractAffiliations(contributor).allMatch(PointService::doesNotHaveId);
  }

  private static boolean hasId(JsonNode affiliation) {
    return nonNull(extractId(affiliation));
  }

  private static boolean doesNotHaveId(JsonNode affiliation) {
    return !hasId(affiliation);
  }

  private static String extractId(JsonNode affiliation) {
    return extractJsonNodeTextValue(affiliation, JSON_PTR_ID);
  }

  private static List<JsonNode> extractCreatorNodes(JsonNode jsonNode) {
    return streamNode(jsonNode.at(JSON_PTR_CONTRIBUTOR)).filter(PointService::isCreator).toList();
  }

  private static boolean hasAffiliations(JsonNode contributor) {
    return !doesNotHaveAffiliations(contributor);
  }

  private static boolean doesNotHaveAffiliations(JsonNode contributor) {
    return contributor.at(JSON_PTR_AFFILIATIONS).isEmpty();
  }

  private static InstanceType extractInstanceType(JsonNode jsonNode) {
    return InstanceType.parse(extractJsonNodeTextValue(jsonNode, JSON_PTR_INSTANCE_TYPE));
  }

  private static Channel extractChannel(InstanceType instanceType, JsonNode jsonNode) {
    var channel =
        switch (instanceType) {
          case ACADEMIC_ARTICLE, ACADEMIC_LITERATURE_REVIEW ->
              jsonNode.at(JSON_PTR_PUBLICATION_CONTEXT).toString();
          case ACADEMIC_MONOGRAPH, ACADEMIC_COMMENTARY -> extractBookChannel(jsonNode);
          case ACADEMIC_CHAPTER -> extractAcademicChapterChannel(jsonNode);
        };
    return attempt(() -> dtoObjectMapper.readValue(channel, Channel.class)).orElseThrow();
  }

  private static String extractAcademicChapterChannel(JsonNode jsonNode) {
    if (nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_SCIENTIFIC_VALUE))
        && isAssigned(
            extractJsonNodeTextValue(jsonNode, JSON_PTR_CHAPTER_SERIES_SCIENTIFIC_VALUE))) {
      return jsonNode.at(JSON_PTR_CHAPTER_SERIES).toString();
    } else {
      return jsonNode.at(JSON_PTR_CHAPTER_PUBLISHER).toString();
    }
  }

  private static String extractBookChannel(JsonNode jsonNode) {
    if (nonNull(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_SCIENTIFIC_VALUE))
        && isAssigned(extractJsonNodeTextValue(jsonNode, JSON_PTR_SERIES_SCIENTIFIC_VALUE))) {
      return jsonNode.at(JSON_PTR_SERIES).toString();
    } else {
      return jsonNode.at(JSON_PTR_PUBLISHER).toString();
    }
  }

  private static boolean isAssigned(String scientificValue) {
    return !UNASSIGNED.equals(scientificValue);
  }

  private static Stream<JsonNode> getJsonNodeStream(JsonNode jsonNode, String jsonPtr) {
    return StreamSupport.stream(jsonNode.at(jsonPtr).spliterator(), false);
  }

  @Deprecated
  private static void massiveHackToFixObjectsWithMultipleTypes(JsonNode jsonNode) {
    fixSeriesType(jsonNode);
    fixJournalType(jsonNode);
  }

  private static void fixJournalType(JsonNode jsonNode) {
    var journal = jsonNode.at(JSON_PTR_PUBLICATION_CONTEXT);
    if (!journal.isMissingNode() && journal.at(JSON_PTR_TYPE).isArray()) {
      var journalObject = (ObjectNode) journal;
      journalObject.remove(TYPE);
      journalObject.put(TYPE, TYPE_JOURNAL);
    }
  }

  private static void fixSeriesType(JsonNode jsonNode) {
    var series = jsonNode.at(JSON_PTR_SERIES);
    if (!series.isMissingNode() && series.at(JSON_PTR_TYPE).isArray()) {
      var seriesObject = (ObjectNode) series;
      seriesObject.remove(TYPE);
      seriesObject.put(TYPE, TYPE_SERIES);
    }
    var chapterSeries = jsonNode.at(JSON_PTR_CHAPTER_SERIES);
    if (!chapterSeries.isMissingNode() && chapterSeries.at(JSON_PTR_TYPE).isArray()) {
      var chapterSeriesObject = (ObjectNode) chapterSeries;
      chapterSeriesObject.remove(TYPE);
      chapterSeriesObject.put(TYPE, TYPE_SERIES);
    }
  }

  private int countCreatorShares(JsonNode jsonNode) {
    var creators = extractCreatorNodes(jsonNode);
    return Integer.sum(
        Integer.sum(
            countVerifiedTopLevelAffiliationsPerCreator(creators),
            countCreatorsWithoutAffiliations(creators)),
        countCreatorsWithOnlyUnverifiedAffiliations(creators));
  }

  private Integer countVerifiedTopLevelAffiliationsPerCreator(List<JsonNode> creators) {
    return creators.stream().map(this::countVerifiedTopLevelAffiliations).reduce(0, Integer::sum);
  }

  // FIXME: Does this actually need to call the organization retriever?
  // This method is only called from EvaluatorService, which has already done the same check.
  // See NP-48364 for more information.
  private Integer countVerifiedTopLevelAffiliations(JsonNode creator) {
    return extractAffiliations(creator)
        .filter(PointService::hasId)
        .map(JsonUtils::extractId)
        .distinct()
        .map(organizationRetriever::fetchOrganization)
        .map(Organization::getTopLevelOrg)
        .map(Organization::id)
        .distinct()
        .map(node -> 1)
        .reduce(0, Integer::sum);
  }
}
