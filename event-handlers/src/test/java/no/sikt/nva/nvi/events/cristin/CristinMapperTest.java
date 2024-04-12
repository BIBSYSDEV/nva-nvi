package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.events.cristin.CristinMapper.AFFILIATION_DELIMITER;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.API_HOST;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.events.cristin.CristinNviReport.Builder;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.Test;

class CristinMapperTest {

    public static final String BASE_POINTS_CRISTIN_ENTRY = "1.0";
    public static final String INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY = "1.3";
    public static final String NO_INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY = "1.0";
    private static final String INSTITUTION_IDENTIFIER = randomString();
    private static final BigDecimal POINTS_PER_CONTRIBUTOR = new BigDecimal("2.1398");
    private static final int CALCULATION_PRECISION = 10;
    private static final int SCALE = 4;
    private static final String CRISTIN_PERSON_IDENTIFIER = randomString();

    @Test
    void shouldThrowNullPointerExceptionWhenQualityCodeIsMissing() {
        var empty = emptyScientificResource();
        var report = CristinNviReport.builder().withScientificResources(List.of(empty)).build();
        assertThrows(NullPointerException.class, () -> CristinMapper.toDbCandidate(report));
    }

    @Test
    void shouldUseCristinLocaleInstitutionWhenSummarizingPoints() {
        var institutionIdentifier = randomString();
        var creators = List.of(scientificPersonAtInstitutionWithPoints(institutionIdentifier, POINTS_PER_CONTRIBUTOR),
                               scientificPersonAtInstitutionWithPoints(institutionIdentifier, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(institutionIdentifier);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report.build());

        var institutionId = dbCandidate.points().get(0).institutionId();
        var expectedInstitutionId = constructExpectedInstitutionId(cristinLocale);
        var expectedPointsForInstitution = POINTS_PER_CONTRIBUTOR.add(POINTS_PER_CONTRIBUTOR,
                                                                      new MathContext(CALCULATION_PRECISION,
                                                                                      RoundingMode.HALF_UP))
                                               .setScale(SCALE, RoundingMode.HALF_UP);

        assertEquals(dbCandidate.points().get(0).points(), expectedPointsForInstitution);
        assertEquals(institutionId, expectedInstitutionId);
    }

    @Test
    void shouldExtractBasePointsFromCreator() {
        var creators = List.of(scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR),
                               scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report.build());

        var expectedBasePoints = new BigDecimal(BASE_POINTS_CRISTIN_ENTRY).setScale(SCALE, RoundingMode.HALF_UP);
        assertEquals(dbCandidate.basePoints(), expectedBasePoints);
    }

    @Test
    void shouldExtractInternationalCollaborationFactorFromCreatorAndSetInternationalCollaborationToTrue() {
        var creators = List.of(scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR),
                               scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report.build());

        var expectedCollaborationFactor = new BigDecimal(INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY).setScale(
            SCALE, RoundingMode.HALF_UP);

        assertEquals(dbCandidate.collaborationFactor(), expectedCollaborationFactor);
        assertTrue(dbCandidate.internationalCollaboration());
    }

    @Test
    void shouldExtractInternationalCollaborationFactorFromCreatorAndSetInternationalCollaborationToFalseWhenNoInternationalCollaboration() {
        var creators = List.of(
            scientificPersonWithNoInternationalCollaboration(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR),
            scientificPersonWithNoInternationalCollaboration(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report.build());

        var expectedCollaborationFactor = new BigDecimal(NO_INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY).setScale(
            SCALE, RoundingMode.HALF_UP);

        assertEquals(dbCandidate.collaborationFactor(), expectedCollaborationFactor);
        assertFalse(dbCandidate.internationalCollaboration());
    }

    @Test
    void shouldSummarizePointsWhenCalculationTotalPoints() {
        var firstInstitution = randomString();
        var secondInstitution = randomString();
        var creators = List.of(
            scientificPersonWithNoInternationalCollaboration(firstInstitution, POINTS_PER_CONTRIBUTOR),
            scientificPersonWithNoInternationalCollaboration(secondInstitution, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var firstLocale = cristinLocaleWithInstitutionIdentifier(firstInstitution);
        var secondLocale = cristinLocaleWithInstitutionIdentifier(secondInstitution);
        var report = cristinReportFromCristinLocalesAndScientificResource(List.of(firstLocale, secondLocale),
                                                                          scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report);

        var expectedTotalPoints = POINTS_PER_CONTRIBUTOR.add(POINTS_PER_CONTRIBUTOR,
                                                             new MathContext(CALCULATION_PRECISION,
                                                                             RoundingMode.HALF_UP))
                                      .setScale(SCALE, RoundingMode.HALF_UP);
        assertEquals(dbCandidate.totalPoints(), expectedTotalPoints);
    }

    @Test
    void shouldExtractChannelIdFromAcademicArticle() {
        var academicArticleReference = """
                            {
              "type" : "Reference",
              "publicationContext" : {
                "type" : "Journal",
                "id" : "https://api.dev.nva.aws.unit.no/publication-channels-v2/journal/6A227640-F250-4909-9F6B-16782393FC15/2015"
              },
              "publicationInstance" : {
                "type" : "AcademicArticle"
              }
            }
            """;

        var nviReport = nviReportWithInstanceTypeAndReference("AcademicArticle", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        var expectedChannelId = URI.create(
            "https://api.dev.nva.aws.unit.no/publication-channels-v2/journal/6A227640-F250-4909-9F6B-16782393FC15"
            + "/2015");
        var expectedChannelType = ChannelType.JOURNAL;

        assertEquals(expectedChannelId, dbCandidate.channelId());
        assertEquals(expectedChannelType, dbCandidate.channelType());
    }

    @Test
    void shouldExtractChannelIdFromAcademicLiteratureReview() {
        var academicArticleReference = """
            {
                  "type" : "Reference",
                  "publicationContext" : {
                    "type" : "Journal",
                    "id" : "https://api.test.nva.aws.unit.no/publication-channels-v2/journal/2D37D55B-90DF-413F-8585-85A970411E34/2020"
                  },
                  "publicationInstance" : {
                    "type" : "AcademicLiteratureReview"
                  }
                }
            """;

        var nviReport = nviReportWithInstanceTypeAndReference("AcademicLiteratureReview", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        var expectedChannelId = URI.create(
            "https://api.test.nva.aws.unit.no/publication-channels-v2/journal/2D37D55B-90DF-413F-8585-85A970411E34"
            + "/2020");
        var expectedChannelType = ChannelType.JOURNAL;

        assertEquals(expectedChannelId, dbCandidate.channelId());
        assertEquals(expectedChannelType, dbCandidate.channelType());
    }

    @Test
    void shouldExtractChannelIdFromAcademicMonographFromSeries() {
        var academicArticleReference = """
            {
                  "type" : "Reference",
                  "publicationContext" : {
                    "type" : "Book",
                    "series" : {
                      "type" : "Series",
                      "id" : "https://api.test.nva.aws.unit.no/publication-channels-v2/series/69CFBB82-5064-402D-843F-B27B246FB7DE/2013"
                    },
                    "seriesNumber" : "Volume:17;Issue:17",
                    "publisher" : {
                      "type" : "Publisher",
                      "id" : "https://api.test.nva.aws.unit.no/publication-channels-v2/publisher/7BB46297-D894-4A65-B113-6462C58DE20A/2013",
                      "valid" : true
                    }
                  },
                  "publicationInstance" : {
                    "type" : "AcademicMonograph"
                  }
                }
            """;

        var nviReport = nviReportWithInstanceTypeAndReference("AcademicMonograph", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        var expectedChannelId = URI.create(
            "https://api.test.nva.aws.unit.no/publication-channels-v2/series/69CFBB82-5064-402D-843F-B27B246FB7DE"
            + "/2013");
        var expectedChannelType = ChannelType.SERIES;

        assertEquals(expectedChannelId, dbCandidate.channelId());
        assertEquals(expectedChannelType, dbCandidate.channelType());
    }

    @Test
    void shouldExtractChannelIdFromAcademicMonographFromPublisher() {
        var academicArticleReference = """
            {
                  "type" : "Reference",
                  "publicationContext" : {
                    "type" : "Book",
                    "publisher" : {
                      "type" : "Publisher",
                      "id" : "https://api.test.nva.aws.unit.no/publication-channels-v2/publisher/7BB46297-D894-4A65-B113-6462C58DE20A/2013",
                      "valid" : true
                    }
                  },
                  "publicationInstance" : {
                    "type" : "AcademicMonograph"
                  }
                }
            """;
        var nviReport = nviReportWithInstanceTypeAndReference("AcademicMonograph", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        var expectedChannelId = URI.create(
            "https://api.test.nva.aws.unit.no/publication-channels-v2/publisher/7BB46297-D894-4A65-B113-6462C58DE20A"
            + "/2013");
        var expectedChannelType = ChannelType.PUBLISHER;

        assertEquals(expectedChannelId, dbCandidate.channelId());
        assertEquals(expectedChannelType, dbCandidate.channelType());
    }

    @Test
    void shouldExtractChannelIdFromAcademicChapterWhenSeriesIsPresent() {
        var academicArticleReference = """
                                                             {
              "type": "Reference",
              "publicationContext": {
                "type": "Anthology",
                "id": "https://api.test.nva.aws.unit.no/publication/018dc266ebf7-09159249-b610-46c3-828d-6e0da6043333",
                "entityDescription": {
                  "type": "EntityDescription",
                  "reference": {
                    "type": "Reference",
                    "publicationContext": {
                      "type": "Book",
                      "series": {
                        "type": "Series",
                        "id": "https://api.test.nva.aws.unit.no/publication-channels-v2/series/69CFBB82-5064-402D-843F-B27B246FB7DE/2013",
                        "scientificValue": "LevelOne"
                      },
                      "publisher": {
                        "type": "Publisher",
                        "id": "https://api.test.nva.aws.unit.no/publication-channels-v2/publisher/7BB46297-D894-4A65-B113-6462C58DE20A/2013",
                        "valid": true
                      }
                    },
                    "publicationInstance": {
                      "type": "AcademicMonograph"
                    }
                  }
                }
              },
              "publicationInstance": {
                "type": "AcademicChapter"
              }
            }
            """;

        var nviReport = nviReportWithInstanceTypeAndReference("AcademicChapter", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        var expectedChannelId = URI.create(
            "https://api.test.nva.aws.unit.no/publication-channels-v2/series/69CFBB82-5064-402D-843F-B27B246FB7DE"
            + "/2013");
        var expectedChannelType = ChannelType.SERIES;

        assertEquals(expectedChannelId, dbCandidate.channelId());
        assertEquals(expectedChannelType, dbCandidate.channelType());
    }

    @Test
    void shouldExtractChannelIdFromAcademicChapter() {
        var academicArticleReference = """
                                      {
              "type": "Reference",
              "publicationContext": {
                "type": "Anthology",
                "id": "https://api.test.nva.aws.unit.no/publication/018dc266ebf7-09159249-b610-46c3-828d-6e0da6043333",
                "entityDescription": {
                  "type": "EntityDescription",
                  "reference": {
                    "type": "Reference",
                    "publicationContext": {
                      "type": "Book",
                      "publisher" : {
                        "type" : "Publisher",
                        "id" : "https://api.test.nva.aws.unit.no/publication-channels-v2/publisher/7BB46297-D894-4A65-B113-6462C58DE20A/2013",
                        "valid" : true
                      }
                    },
                    "publicationInstance": {
                      "type": "AcademicMonograph"
                    }
                  }
                }
              },
              "publicationInstance": {
                "type": "AcademicChapter"
              }
            }
                                                """;

        var nviReport = nviReportWithInstanceTypeAndReference("AcademicChapter", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        var expectedChannelId = URI.create(
            "https://api.test.nva.aws.unit.no/publication-channels-v2/publisher/7BB46297-D894-4A65-B113-6462C58DE20A"
            + "/2013");
        var expectedChannelType = ChannelType.PUBLISHER;

        assertEquals(expectedChannelId, dbCandidate.channelId());
        assertEquals(expectedChannelType, dbCandidate.channelType());
    }

    @Test
    void shouldNotExtractChannelIdAndChannelTypeWhenUnsupportedInstanceTypeInReference() {
        var academicArticleReference = """
                                      {
            
              "type": "Reference",
              "publicationContext": {
                "type": "Anthology",
                "id": "https://api.test.nva.aws.unit.no/publication/018dc266ebf7-09159249-b610-46c3-828d-6e0da6043333"
              },
              "publicationInstance": {
                "type": "UnsupportedType"
              }
            }
            """;

        var nviReport = nviReportWithInstanceTypeAndReference("AcademicChapter", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        assertNull(dbCandidate.channelId());
        assertNull(dbCandidate.channelType());
    }

    @Test
    void shouldNotExtractChannelIdAndChannelTypeWhenUnsupportedInstanceType() {
        var academicArticleReference = """
                                      {
              "type": "Reference",
              "publicationContext": {
                "type": "Anthology",
                "id": "https://api.test.nva.aws.unit.no/publication/018dc266ebf7-09159249-b610-46c3-828d-6e0da6043333"
              },
              "publicationInstance": {
                "type": "UnsupportedType"
              }
            }
            """;

        var nviReport = nviReportWithInstanceTypeAndReference("UnsupportedType", academicArticleReference);
        var dbCandidate = CristinMapper.toDbCandidate(nviReport);

        assertNull(dbCandidate.channelId());
        assertNull(dbCandidate.channelType());
    }

    @Test
    void shouldMapCreatorAffiliationPointsWhenSingleCreatorAtInstitution() {
        var creator = scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR);
        var scientificResource = scientificResourceWithCreators(List.of(creator));
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report.build());

        var pointsPerAffiliation = dbCandidate.points().get(0)
                                       .creatorAffiliationPoints().get(0);

        var expectedCreatorPoints = POINTS_PER_CONTRIBUTOR.setScale(SCALE, RoundingMode.HALF_UP);
        assertEquals(expectedCreatorPoints, pointsPerAffiliation.points());
    }

    @Test
    void shouldMapCreatorAffiliationPointsWhenMultipleCreatorsAtInstitution() {
        var firstCreator = scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR);
        var secondCreator = scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR);
        var scientificResource = scientificResourceWithCreators(List.of(firstCreator, secondCreator));
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report.build());

        var institutionPoints = dbCandidate.points().get(0);
        var expectedSingleCreatorPoints = POINTS_PER_CONTRIBUTOR.setScale(SCALE, RoundingMode.HALF_UP);

        institutionPoints.creatorAffiliationPoints().forEach(creatorPoints -> {
            assertEquals(creatorPoints.points(), expectedSingleCreatorPoints);
            assertThat(creatorPoints.creatorId().toString(), containsString(CRISTIN_PERSON_IDENTIFIER));
            }
        );
    }

    private static CristinNviReport nviReportWithInstanceTypeAndReference(String instanceType, String reference) {
        var institutionIdentifier = randomString();
        var creators = List.of(scientificPersonAtInstitutionWithPoints(institutionIdentifier, POINTS_PER_CONTRIBUTOR),
                               scientificPersonAtInstitutionWithPoints(institutionIdentifier, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(institutionIdentifier);
        return cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource).withInstanceType(
            instanceType).withReference(attempt(() -> JsonUtils.dtoObjectMapper.readTree(reference)).orElseThrow()).build();
    }

    private static ScientificResource scientificResourceWithCreators(List<ScientificPerson> creators) {
        return ScientificResource.build().withScientificPeople(creators).withQualityCode(randomString()).build();
    }

    private static Builder cristinReportFromCristinLocalesAndScientificResource(CristinLocale cristinLocales,
                                                                                ScientificResource scientificResource) {
        return CristinNviReport.builder()
                   .withPublicationIdentifier(randomString())
                   .withYearReported(randomString())
                   .withInstanceType(randomString())
                   .withPublicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .withCristinLocales(List.of(cristinLocales))
                   .withReference(attempt(() -> JsonUtils.dtoObjectMapper.readTree("{}")).orElseThrow())
                   .withInstanceType(randomString())
                   .withScientificResources(List.of(scientificResource));
    }

    private static CristinNviReport cristinReportFromCristinLocalesAndScientificResource(
        List<CristinLocale> cristinLocales, ScientificResource scientificResource) {
        return CristinNviReport.builder()
                   .withPublicationIdentifier(randomString())
                   .withYearReported(randomString())
                   .withInstanceType(randomString())
                   .withPublicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .withCristinLocales(cristinLocales)
                   .withScientificResources(List.of(scientificResource))
                   .build();
    }

    private static CristinLocale cristinLocaleWithInstitutionIdentifier(String institutionIdentifier) {
        return CristinLocale.builder()
                   .withDepartmentIdentifier(randomString())
                   .withSubDepartmentIdentifier(randomString())
                   .withDepartmentIdentifier(randomString())
                   .withGroupIdentifier(randomString())
                   .withInstitutionIdentifier(institutionIdentifier)
                   .build();
    }

    private static ScientificPerson scientificPersonAtInstitutionWithPoints(String institutionIdentifier,
                                                                            BigDecimal points) {
        return ScientificPerson.builder()
                   .withInstitutionIdentifier(institutionIdentifier)
                   .withDepartmentIdentifier(randomString())
                   .withSubDepartmentIdentifier(randomString())
                   .withGroupIdentifier(randomString())
                   .withPublicationTypeLevelPoints(BASE_POINTS_CRISTIN_ENTRY)
                   .withAuthorPointsForAffiliation(points.toString())
                   .withCollaborationFactor(INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY)
                   .withCristinPersonIdentifier(CRISTIN_PERSON_IDENTIFIER)
                   .build();
    }

    private static ScientificPerson scientificPersonWithNoInternationalCollaboration(String institutionIdentifier,
                                                                                     BigDecimal points) {
        return ScientificPerson.builder()
                   .withInstitutionIdentifier(institutionIdentifier)
                   .withDepartmentIdentifier(randomString())
                   .withSubDepartmentIdentifier(randomString())
                   .withGroupIdentifier(randomString())
                   .withPublicationTypeLevelPoints(BASE_POINTS_CRISTIN_ENTRY)
                   .withAuthorPointsForAffiliation(points.toString())
                   .withCollaborationFactor(NO_INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY)
                   .build();
    }

    private URI constructExpectedInstitutionId(CristinLocale locale) {
        var identifier = locale.getInstitutionIdentifier()
                         + AFFILIATION_DELIMITER
                         + locale.getDepartmentIdentifier()
                         + AFFILIATION_DELIMITER
                         + locale.getSubDepartmentIdentifier()
                         + AFFILIATION_DELIMITER
                         + locale.getGroupIdentifier();
        return UriWrapper.fromHost(API_HOST).addChild("cristin").addChild("organization").addChild(identifier).getUri();
    }

    private ScientificResource emptyScientificResource() {
        return ScientificResource.build().build();
    }
}