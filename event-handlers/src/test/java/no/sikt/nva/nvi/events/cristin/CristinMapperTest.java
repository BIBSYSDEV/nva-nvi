package no.sikt.nva.nvi.events.cristin;

import static no.sikt.nva.nvi.events.cristin.CristinMapper.AFFILIATION_DELIMITER;
import static no.sikt.nva.nvi.events.cristin.CristinMapper.API_HOST;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.Test;

class CristinMapperTest {

    private static final String INSTITUTION_IDENTIFIER = randomString();
    public static final String BASE_POINTS_CRISTIN_ENTRY = "1.0";
    public static final String INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY = "1.3";
    public static final String NO_INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY = "1.0";
    private static final BigDecimal POINTS_PER_CONTRIBUTOR = new BigDecimal("2.1398");
    private static final int CALCULATION_PRECISION = 10;
    private static final int SCALE = 4;

    @Test
    void shouldThrowNullPointerExceptionWhenQualityCodeIsMissing() {
        var empty = emptyScientificResource();
        var report = CristinNviReport.builder().withScientificResources(List.of(empty)).build();
        assertThrows(NullPointerException.class, () -> CristinMapper.toDbCandidate(report));
    }

    @Test
    void shouldUseCristinLocaleInstitutionWhenSummarizingPoints() {
        var institutionIdentifier = randomString();
        var creators = List.of(scientificPersonAtInstitutionWithPoints(institutionIdentifier,
                                                                       POINTS_PER_CONTRIBUTOR),
                               scientificPersonAtInstitutionWithPoints(institutionIdentifier, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(institutionIdentifier);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report);

        var institutionId = dbCandidate.points().get(0).institutionId();
        var expectedInstitutionId = constructExpectedInstitutionId(cristinLocale);
        var expectedPointsForInstitution = POINTS_PER_CONTRIBUTOR
                                               .add(POINTS_PER_CONTRIBUTOR, new MathContext(CALCULATION_PRECISION, RoundingMode.HALF_UP))
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
        var dbCandidate = CristinMapper.toDbCandidate(report);

        var expectedBasePoints = new BigDecimal(BASE_POINTS_CRISTIN_ENTRY).setScale(1, RoundingMode.HALF_UP);
        assertEquals(dbCandidate.basePoints(), expectedBasePoints);
    }

    @Test
    void shouldExtractInternationalCollaborationFactorFromCreatorAndSetInternationalCollaborationToTrue() {
        var creators = List.of(scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR),
                               scientificPersonAtInstitutionWithPoints(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report);

        var expectedCollaborationFactor = new BigDecimal(INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY)
                                              .setScale(1, RoundingMode.HALF_UP);

        assertEquals(dbCandidate.collaborationFactor(), expectedCollaborationFactor);
        assertTrue(dbCandidate.internationalCollaboration());
    }

    @Test
    void shouldExtractInternationalCollaborationFactorFromCreatorAndSetInternationalCollaborationToFalseWhenNoInternationalCollaboration() {
        var creators = List.of(scientificPersonWithNoInternationalCollaboration(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR),
                               scientificPersonWithNoInternationalCollaboration(INSTITUTION_IDENTIFIER, POINTS_PER_CONTRIBUTOR));
        var scientificResource = scientificResourceWithCreators(creators);
        var cristinLocale = cristinLocaleWithInstitutionIdentifier(INSTITUTION_IDENTIFIER);
        var report = cristinReportFromCristinLocalesAndScientificResource(cristinLocale, scientificResource);
        var dbCandidate = CristinMapper.toDbCandidate(report);

        var expectedCollaborationFactor = new BigDecimal(NO_INTERNATIONAL_COLLABORATION_FACTOR_CRISTIN_ENTRY)
                                              .setScale(1, RoundingMode.HALF_UP);

        assertEquals(dbCandidate.collaborationFactor(), expectedCollaborationFactor);
        assertFalse(dbCandidate.internationalCollaboration());
    }

    private static ScientificResource scientificResourceWithCreators(List<ScientificPerson> creators) {
        return ScientificResource.build()
                   .withScientificPeople(creators)
                   .withQualityCode(randomString())
                   .build();
    }

    private static CristinNviReport cristinReportFromCristinLocalesAndScientificResource(CristinLocale cristinLocales,
                                                                                         ScientificResource scientificResource) {
        return CristinNviReport.builder()
                   .withPublicationIdentifier(randomString())
                   .withYearReported(randomString())
                   .withInstanceType(randomString())
                   .withPublicationDate(new PublicationDate(randomString(), randomString(), randomString()))
                   .withCristinLocales(List.of(cristinLocales))
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