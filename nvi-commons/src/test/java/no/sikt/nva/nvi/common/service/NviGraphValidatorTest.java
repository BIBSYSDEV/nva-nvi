package no.sikt.nva.nvi.common.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NviGraphValidatorTest {

  private static final String NVA_ONTOLOGY = "https://nva.sikt.no/ontology/publication#";
  private static final String PUBLICATION = "Publication";
  private static final String YEAR_PROPERTY = "year";
  private static final String PUBLICATION_DATE_CLASS = "PublicationDate";
  private static final String CONTRIBUTOR_CLASS = "Contributor";
  private static final String PUBLICATION_CHANNEL_CLASS = "PublicationChannel";
  private static final String NAME_PROPERTY = "name";
  private static final String IDENTIFIER_PROPERTY = "identifier";
  private static final String ROLE_PROPERTY = "role";
  private static final String TITLE_PROPERTY = "title";
  private static final String STATUS_PROPERTY = "status";
  private static final String PUBLICATION_TYPE_PROPERTY = "publicationType";
  private static final String ABSTRACT_TEXT_PROPERTY = "abstractText";
  private static final String PRINT_ISSN_PROPERTY = "printIssn";
  private static final String PUBLICATION_CHANNEL_PROPERTY = "publicationChannel";
  private NviGraphValidator nviGraphValidator;
  private Logger logger;

  public static Stream<Named<Object>> invalidPublicationIdentifierProvider() {
    return Stream.of(
        Named.of("UUID with extra numbers", UUID.randomUUID() + "123"),
        Named.of(
            "Badly trimmed SortableIdentifier", SortableIdentifier.next().toString().substring(2)));
  }

  private static Stream<Named<URI>> channelUriProvider() {
    var hosts =
        List.of(
            "api.dev.nva.aws.unit.no",
            "api.e2e.nva.aws.unit.no",
            "api.sandbox.nva.aws.unit.no",
            "api.test.nva.aws.unit.no",
            "api.nva.unit.no");
    var types = List.of("publisher", "serial-publication");
    var template =
        "https://%s/publication-channels-v2/%s/CEB75710-B16D-4E32-8DA8-041470CCAD61/2008";
    var uris = new ArrayList<Named<URI>>();
    for (var host : hosts) {
      for (var type : types) {
        uris.add(
            Named.of(
                "URI host %s, type %s".formatted(host, type),
                URI.create(template.formatted(host, type))));
      }
    }
    return uris.stream();
  }

  @BeforeEach
  void setUp() {
    logger = LoggerFactory.getLogger(NviGraphValidator.class);
    nviGraphValidator = new NviGraphValidator();
  }

  @Test
  void shouldNotLogErrorsWhenThereAreNoErrors() {
    final var testAppender = LogUtils.getTestingAppender(NviGraphValidator.class);
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(model);
    validation.log(logger);
    assertFalse(validation.hasViolations());
    assertThat(testAppender.getMessages()).isEmpty();
  }

  @Test
  void shouldLogErrorsWhenThereIsNoData() {
    final var testAppender = LogUtils.getTestingAppender(NviGraphValidator.class);
    var model = ModelFactory.createDefaultModel();
    var validation = nviGraphValidator.validate(model);
    validation.log(logger);
    assertTrue(validation.hasViolations());
    assertThat(testAppender.getMessages())
        .containsSequence("Model validation failed: Publication is missing");
  }

  @Test
  void shouldReportMissingPublicationAndNothingMore() {
    var model = ModelFactory.createDefaultModel();
    var validation = nviGraphValidator.validate(model);
    assertThat(validation.generateReport()).containsSequence("Publication is missing").hasSize(1);
  }

  @Test
  void shouldReportViolationWhenThereIsMoreThanOnePublication() {
    var data =
        """
        @prefix : <%s> .
        :a a :Publication .
        :b a :Publication .
        """
            .formatted(NVA_ONTOLOGY);
    var validation =
        nviGraphValidator.validate(addToModel(ModelFactory.createDefaultModel(), data));
    assertThat(validation.generateReport())
        .containsSequence("The graph contains more than one node with type Publication");
  }

  @Test
  void shouldReportWhenContributorsAreMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeContributors(model));
    assertThat(validation.generateReport())
        .containsSequence(
            "Publication does not have at least one verified or unverified contributor")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorsHaveWrongType() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replaceContributorWithInvalidValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication contributor is not a value of correct type")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationIdentifierIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationIdentifier(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication identifier is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationIdentifierIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addAdditionalPublicationIdentifier(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication identifier is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationisNotString() {
    var model = createModelWithNoErrors();
    var uri = URI.create("https://example.org/publication/" + UUID.randomUUID());
    var validation =
        nviGraphValidator.validate(replacePublicationIdentifierWithInvalidValue(model, uri));
    assertThat(validation.generateReport())
        .containsSequence("Publication identifier is not a string")
        // It is also generally invalid since it cannot match the required pattern, so two errors
        // not just this one
        .hasSize(2);
  }

  @ParameterizedTest
  @DisplayName("Should report when Publication identifier is invalid")
  @MethodSource("invalidPublicationIdentifierProvider")
  void shouldReportWhenPublicationIdentifierIsInvalid(String value) {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replacePublicationIdentifierWithInvalidValue(model, value));
    assertThat(validation.generateReport())
        .containsSequence("Publication identifier is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenModifiedDateIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeModifiedDate(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication modified date is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenModifiedDateIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(repeatModifiedDate(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication modified date is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenModifiedDateIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replaceModifiedDateWithInvalidValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication modified date is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationAbstractIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeAbstract(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication abstract is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationAbstractIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addAbstract(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication abstract is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationAbstractIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addInvalidAbstract(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication abstract is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationApplicabilityIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeIsApplicable(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication applicability is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationApplicabilityIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addIsApplicable(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication applicability is repeated")
        // A graph needs distinct values and false causes an additional error.
        .hasSize(2);
  }

  @Test
  void shouldReportWhenPublicationApplicabilityIsNotBoolean() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replaceIsApplicableWithNoBoolean(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication applicability is not a boolean")
        // We end up with two errors since it cannot fulfil requirements for being true if it is not
        // a boolean
        .hasSize(2);
  }

  private Model replaceIsApplicableWithNoBoolean(Model model) {
    var removeModel = removeIsApplicable(model);
    return addTriples(removeModel, addQuery(PUBLICATION, "isApplicable", "Not boolean"));
  }

  @Test
  void shouldReportWhenPublicationApplicabilityIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replaceApplicableValueWithFalse(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication applicability is not TRUE")
        .hasSize(1);
  }

  private Model replaceApplicableValueWithFalse(Model model) {
    var removeModel = removeIsApplicable(model);
    return addTriples(removeModel, addQuery(PUBLICATION, "isApplicable", false));
  }

  @Test
  void shouldReportWhenPublicationInternationalCollaborationIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeInternationalCollaboration(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication international collaboration is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationInternationalCollaborationIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addIInternationalCollaboration(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication international collaboration is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationInternationalCollaborationIsNotBoolean() {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replaceInternationalCollaborationWithNonBoolean(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication international collaboration is not a boolean")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationLanguageIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addILanguage(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication language is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationLanguageIsInvalidIri() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(swapLanguageWithInvalidIri(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication language is not a valid IRI")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationPageCountIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePageCount(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication page count is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationPageCountIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addIPageCount(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication page count is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannel(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIsRepeated() {
    var model = createModelWithNoErrors();
    var modelWithTwoChannels = addPublicationChannel(model);
    var modelWithThreeChannels = addPublicationChannel(modelWithTwoChannels);
    var validation = nviGraphValidator.validate(modelWithThreeChannels);
    assertThat(validation.generateReport())
        .containsSequence("Publication channel is repeated more than twice")
        .hasSize(1);
  }

  @ParameterizedTest
  @DisplayName("Should not report when publication channel is valid")
  @MethodSource("channelUriProvider")
  void shouldNotReportWhenPublicationChannelIsValid(URI channel) {
    var model = createModelWithNoErrors();
    var modelWithValidChannel = swapPublicationChannelWithValidIri(model, channel);
    var validation = nviGraphValidator.validate(modelWithValidChannel);
    assertThat(validation.hasViolations()).isFalse();
  }

  @Test
  void shouldReportWhenPublicationChannelIriIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(swapPublicationChannelWithInvalidIri(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel IRI is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationDateIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationDate(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationDateIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationDate(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date is repeated")
        .hasSize(1);
  }

  @Test
  void shouldNotReportWhenPublicationDateIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addInvalidPublicationDate(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date is not a blank node")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTypeIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication type is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTypeIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication type is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTypeIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationTypeWithInvalidType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication type is not one of the allowed types")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationStatusIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationStatus(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication status is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationStatusIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationStatus(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication status is repeated")
        // Because there is only one valid status, and we are using a graph, we end up with an
        // invalid status and therefore two errors.
        .hasSize(2);
  }

  @Test
  void shouldReportWhenPublicationStatusIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationStatusWithInvalidType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication status is not required type PUBLISHED")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTitleIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationTitle(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication title is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTitleIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationTitle(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication title is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTitleIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationTitleWithInvalidType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication title is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTopLevelOrganizationIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationTopLevelOrganization(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication top-level organization is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationTopLevelOrganizationIsInvalid() {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replacePublicationTopLevelOrganizationWithInvalidType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication top-level organization is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorAffiliationIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeContributorAffiliation(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor affiliation is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorAffiliationIsInvalid() {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replaceContributorAffiliationWithInvalidData(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor affiliation is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorNameIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeContributorName(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor name is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorNameIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addContributorName(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor name is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorNameIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replaceContributorNameWithInvalidValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor name is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorRoleIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeContributorRole(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor role is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorRoleIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addContributorRole(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor role is repeated")
        // Because we have to pass in an invalid role to create a duplicate (graphs cannot have
        // duplicates), we have two errors.
        .hasSize(2);
  }

  @Test
  void shouldReportWhenContributorRoleIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replaceContributorRoleWithInvalidValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor role is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorVerificationStatusIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeContributorVerificationStatus(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor verification status is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorVerificationStatusIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addContributorVerificationStatus(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor verification status is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenContributorVerificationStatusIsInvalid() {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replaceContributorVerificationStatusWithInvalidValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Contributor verification status is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIssnIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannelIssn(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel print ISSN is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIssnIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationChannelIssn(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel print ISSN is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIssnIsInvalid() {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replacePublicationChannelIssnWithInvalidValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel print ISSN is invalid")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelYearIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannelYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel year is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelYearIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationChannelYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel year is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelYearIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationChannelYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel year is invalid")
        // Will include IRI-validation error since there is no match
        .hasSize(2);
  }

  @Test
  void shouldReportWhenPublicationChannelYearDoesNotMatchPublicationChannelIri() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationChannelYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel year does not match IRI")
        // Will include year-validation error since there is no match
        .hasSize(2);
  }

  @Test
  void shouldReportWhenPublicationChannelScientificValueIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannelScientificValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel scientific value is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelScientificValueIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationChannelScientificValue(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel scientific value is repeated")
        .hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NonsenseLevel", "Unassigned", "X"})
  void shouldReportWhenPublicationChannelScientificValueIsInvalid(String invalidLevel) {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(replacePublicationChannelScientificValue(model, invalidLevel));
    assertThat(validation.generateReport())
        .containsSequence(
            "Publication channel scientific value is not one of [ LevelOne, LevelTwo ]")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelScientificValueIsInvalidType() {
    var model = createModelWithNoErrors();
    var validation =
        nviGraphValidator.validate(
            replacePublicationChannelScientificValue(model, URI.create("https://example.org/X")));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel scientific value is not a string")
        // Size is two since the value also cannot match the required (string) values.
        .hasSize(2);
  }

  @Test
  void shouldReportWhenPublicationChannelIdentifierIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannelIdentifier(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel identifier is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIdentifierIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationChannelIdentifier(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel identifier has multiple values")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelIdentifierIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationChannelIdentifier(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel identifier is not a string")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelNameIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannelName(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel name is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelNameIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationChannelName(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel name is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelNameIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationChannelName(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel name is not a string")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelTypeIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationChannelType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel type is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelTypeIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationChannelType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel type is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationChannelTypeIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationChannelType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication channel type is not a valid URI")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationDateYearsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationDateYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date year is missing")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationDateYearIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addPublicationDateYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date year is repeated")
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationDateYearIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationDateYear(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date year is invalid")
        .hasSize(1);
  }

  private Model replacePublicationDateYear(Model model) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION_DATE_CLASS, YEAR_PROPERTY));
    return addTriples(removeModel, addQuery("PublicationDate", "year", "MMXXV"));
  }

  private String addQuery(String targetClass, String addProperty, Object value) {
    return """
           PREFIX : <%s>
           CONSTRUCT {
             ?subject :%s %s .
           } WHERE {
             ?subject a :%s .
           }
           """
        .formatted(NVA_ONTOLOGY, addProperty, createObject(value), targetClass);
  }

  private static String createObject(Object value) {
    return switch (value) {
      case String string -> createObjectFromString(string);
      case Number number -> number.toString();
      case Boolean bool -> "\"" + bool + "\"^^<http://www.w3.org/2001/XMLSchema#boolean>";
      case URI uri -> "<" + uri + ">";
      case null, default -> throw new IllegalArgumentException("Unsupported value " + value);
    };
  }

  private static String createObjectFromString(String string) {
    return string.startsWith("Class:")
        ? ":" + string.replaceAll("Class:", "")
        : "\"" + string + "\"";
  }

  private Model removePublicationDateYear(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_DATE_CLASS, YEAR_PROPERTY));
  }

  private Model addPublicationDateYear(Model model) {
    return addTriples(model, addQuery(PUBLICATION_DATE_CLASS, YEAR_PROPERTY, "7777"));
  }

  @Test
  void shouldReportWhenPublicationDateTypeIsUndefined() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removePublicationDateType(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication date type is undefined")
        .hasSize(1);
  }

  private Model removePublicationDateType(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject a :PublicationDate .
        } WHERE {
          [] :publicationDate ?subject .
          ?subject a :PublicationDate .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return removeTriples(model, query);
  }

  private Model removePublicationChannelType(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "channelType"));
  }

  private Model addPublicationChannelType(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :channelType ?channelType .
        } WHERE {
          ?subject a :PublicationChannel ;
                   :channelType ?current .
          BIND(IF(?current = :Journal, :Publisher, :Journal) AS ?channelType)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replacePublicationChannelType(Model model) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "channelType"));
    return addTriples(
        removeModel,
        addQuery(PUBLICATION_CHANNEL_CLASS, "channelType", "Class:NonsensicalChannelType"));
  }

  private Model removePublicationChannelName(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, NAME_PROPERTY));
  }

  private Model addPublicationChannelName(Model model) {
    return addTriples(
        model, addQuery(PUBLICATION_CHANNEL_CLASS, NAME_PROPERTY, "Another name for the thing"));
  }

  private Model replacePublicationChannelName(Model model) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, NAME_PROPERTY));
    return addTriples(
        removeModel,
        addQuery(PUBLICATION_CHANNEL_CLASS, NAME_PROPERTY, URI.create("https://example.org/abc")));
  }

  private Model removePublicationChannelIdentifier(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, IDENTIFIER_PROPERTY));
  }

  private Model addPublicationChannelIdentifier(Model model) {
    return addTriples(
        model,
        addQuery(PUBLICATION_CHANNEL_CLASS, IDENTIFIER_PROPERTY, UUID.randomUUID().toString()));
  }

  private Model replacePublicationChannelIdentifier(Model model) {
    var removedModel =
        removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, IDENTIFIER_PROPERTY));
    return addTriples(
        removedModel,
        addQuery(PUBLICATION_CHANNEL_CLASS, IDENTIFIER_PROPERTY, "Class:NonsensicalIdentifier"));
  }

  private Model removePublicationChannelScientificValue(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "scientificValue"));
  }

  private Model addPublicationChannelScientificValue(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :scientificValue ?level .
        } WHERE {
          ?subject :scientificValue ?current .
          BIND(IF(?current = "LevelOne", "LevelTwo", "LevelOne") AS ?level)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replacePublicationChannelScientificValue(Model model, Object invalidLevel) {
    var removedModel =
        removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "scientificValue"));
    return addTriples(
        removedModel, addQuery(PUBLICATION_CHANNEL_CLASS, "scientificValue", invalidLevel));
  }

  private Model replacePublicationChannelYear(Model model) {
    var removed = removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, YEAR_PROPERTY));
    return addTriples(removed, addQuery(PUBLICATION_CHANNEL_CLASS, YEAR_PROPERTY, "MMXXV"));
  }

  private Model addPublicationChannelYear(Model model) {
    return addTriples(model, addQuery(PUBLICATION_CHANNEL_CLASS, YEAR_PROPERTY, "1777"));
  }

  private Model removePublicationChannelYear(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, YEAR_PROPERTY));
  }

  private Model removePublicationChannelIssn(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, PRINT_ISSN_PROPERTY));
  }

  private Model addPublicationChannelIssn(Model model) {
    return addTriples(model, addQuery(PUBLICATION_CHANNEL_CLASS, PRINT_ISSN_PROPERTY, "2999-111X"));
  }

  private Model replacePublicationChannelIssnWithInvalidValue(Model model) {
    var removeTriples =
        removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, PRINT_ISSN_PROPERTY));
    return addTriples(
        removeTriples, addQuery(PUBLICATION_CHANNEL_CLASS, PRINT_ISSN_PROPERTY, "2999-111GUM"));
  }

  private Model removeContributorVerificationStatus(Model model) {
    return removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "verificationStatus"));
  }

  private Model addContributorVerificationStatus(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :verificationStatus ?object .
        } WHERE {
          ?subject a :Contributor ;
                   :verificationStatus ?current .
          BIND(IF(?current = "Verified", "NotVerified", "Verified") AS ?object)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replaceContributorVerificationStatusWithInvalidValue(Model model) {
    var removeTriples = removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "verificationStatus"));
    return addTriples(
        removeTriples,
        addQuery(CONTRIBUTOR_CLASS, "verificationStatus", "Some string that is not a class"));
  }

  private Model removeContributorRole(Model model) {
    return removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, ROLE_PROPERTY));
  }

  private Model addContributorRole(Model model) {
    return addTriples(model, addQuery(CONTRIBUTOR_CLASS, ROLE_PROPERTY, "Class:Photographer"));
  }

  private Model replaceContributorRoleWithInvalidValue(Model model) {
    var removeTriples = removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, ROLE_PROPERTY));
    return addTriples(
        removeTriples, addQuery(CONTRIBUTOR_CLASS, ROLE_PROPERTY, "Class:Photographer"));
  }

  private Model replaceContributorNameWithInvalidValue(Model model) {
    var removeTriples = removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, NAME_PROPERTY));
    return addTriples(removeTriples, addQuery(CONTRIBUTOR_CLASS, NAME_PROPERTY, 222));
  }

  private Model addContributorName(Model model) {
    return addTriples(model, addQuery(CONTRIBUTOR_CLASS, NAME_PROPERTY, "Hockey McWilson"));
  }

  private Model removeContributorName(Model model) {
    return removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, NAME_PROPERTY));
  }

  private Model replaceContributorAffiliationWithInvalidData(Model model) {
    var removeTriples = removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "affiliation"));
    return addTriples(
        removeTriples, addQuery(CONTRIBUTOR_CLASS, "affiliation", "Milky way galaxy"));
  }

  private Model removeContributorAffiliation(Model model) {
    var query = removeQuery(CONTRIBUTOR_CLASS, "affiliation");
    return removeTriples(model, query);
  }

  private Model removePublicationTopLevelOrganization(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "topLevelOrganization"));
  }

  private Model replacePublicationTopLevelOrganizationWithInvalidType(Model model) {
    var removeTriples = removeTriples(model, removeQuery("Publication", "topLevelOrganization"));
    return addTriples(
        removeTriples,
        addQuery(PUBLICATION, "topLevelOrganization", "A string not an organization"));
  }

  private Model removePublicationTitle(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, TITLE_PROPERTY));
  }

  private Model addPublicationTitle(Model model) {
    return addTriples(model, addQuery(PUBLICATION, TITLE_PROPERTY, "Some additional title"));
  }

  private Model replacePublicationTitleWithInvalidType(Model model) {
    var addQuery =
        addQuery(PUBLICATION, TITLE_PROPERTY, URI.create("https://example.org/not-a-title"));
    var removeQuery = removeQuery(PUBLICATION, TITLE_PROPERTY);
    return addTriples(removeTriples(model, removeQuery), addQuery);
  }

  private Model addPublicationStatus(Model model) {
    return addTriples(model, additionalValueQuery(PUBLICATION, STATUS_PROPERTY, "UNPUBLISHED"));
  }

  private Model removePublicationStatus(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, STATUS_PROPERTY));
  }

  private Model replacePublicationStatusWithInvalidType(Model model) {
    var status = "Class:NONSENSE_STATUS";
    var addQuery = addQuery(PUBLICATION, STATUS_PROPERTY, status);
    var removeQuery = removeQuery(PUBLICATION, STATUS_PROPERTY);
    return addTriples(removeTriples(model, removeQuery), addQuery);
  }

  private Model addInvalidPublicationDate(Model model) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION, "publicationDate"));
    return addTriples(removeModel, addPublicationDateAsIriQuery());
  }

  private String addPublicationDateAsIriQuery() {
    return """
           PREFIX : <%s>
           CONSTRUCT {
             ?subject :publicationDate <https://example.com/publicationDate> .
             <https://example.com/publicationDate> a :PublicationDate ;
               :year "2025" .
           } WHERE {
             ?subject a :Publication .
           }
           """
        .formatted(NVA_ONTOLOGY);
  }

  private Model removePublicationType(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, PUBLICATION_TYPE_PROPERTY));
  }

  private Model addPublicationType(Model model) {
    return addTriples(
        model, additionalValueQuery(PUBLICATION, PUBLICATION_TYPE_PROPERTY, "AcademicMonograph"));
  }

  private Model replacePublicationTypeWithInvalidType(Model model) {
    var type = "Class:NonsenseCategory";
    var addQuery = addQuery(PUBLICATION, PUBLICATION_TYPE_PROPERTY, type);
    var removeQuery = removeQuery(PUBLICATION, PUBLICATION_TYPE_PROPERTY);
    return addTriples(removeTriples(model, removeQuery), addQuery);
  }

  private Model addPublicationDate(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :publicationDate [
            a :PublicationDate ;
            :year "3033"
          ] .
        } WHERE {
          ?subject :publicationDate [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model removePublicationDate(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "publicationDate"));
  }

  private Model swapPublicationChannelWithValidIri(Model model, URI channel) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION, PUBLICATION_CHANNEL_PROPERTY));
    var addQuery = addQuery(PUBLICATION, PUBLICATION_CHANNEL_PROPERTY, channel);
    return addTriples(removeModel, addQuery);
  }

  private Model swapPublicationChannelWithInvalidIri(Model model) {
    var removeQuery = removeQuery(PUBLICATION, PUBLICATION_CHANNEL_PROPERTY);
    var addQuery =
        addQuery(
            PUBLICATION, PUBLICATION_CHANNEL_PROPERTY, URI.create("https://example.org/channel"));
    return addTriples(removeTriples(model, removeQuery), addQuery);
  }

  private Model removePublicationChannel(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, PUBLICATION_CHANNEL_PROPERTY));
  }

  private Model addPublicationChannel(Model model) {
    var uuid = UUID.randomUUID().toString();
    var query =
        """
PREFIX : <%s>
CONSTRUCT {
  ?subject :publicationChannel <https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/%s/1973> .
  <https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/%s/1973> a :PublicationChannel ;
        :printIssn "2159-484X" ;
        :year "1973" ;
        :scientificValue "LevelTwo" ;
        :name "Another IEEE International Conference on Software Testing Verification and Validation Workshop, ICSTW" ;
        :identifier "%s" ;
        :channelType :Journal .
} WHERE {
  ?subject :publicationChannel [] .
}
"""
            .formatted(NVA_ONTOLOGY, uuid, uuid, uuid);
    return addTriples(model, query);
  }

  private Model removePageCount(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "pageCount"));
  }

  private Model addIPageCount(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :pageCount [
            a :PageCount ;
            :first "1000" ;
            :last "2000" ;
            :total "1000"
          ] .
        } WHERE {
          ?subject :pageCount [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model addILanguage(Model model) {
    var query = addQuery(PUBLICATION, "language", URI.create("http://lexvo.org/id/iso639-3/cym"));
    return addTriples(model, query);
  }

  private Model swapLanguageWithInvalidIri(Model model) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION, "language"));
    return addTriples(
        removeModel,
        addQuery(PUBLICATION, "language", URI.create("https://example.org/language/1")));
  }

  private Model removeInternationalCollaboration(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "isInternationalCollaboration"));
  }

  private Model addIInternationalCollaboration(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :isInternationalCollaboration ?addition .
        } WHERE {
          ?subject :isInternationalCollaboration ?object .
          BIND (!?object as ?addition)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replaceInternationalCollaborationWithNonBoolean(Model model) {
    var removeModel = removeInternationalCollaboration(model);
    return addTriples(
        removeModel, addQuery(PUBLICATION, "isInternationalCollaboration", "Not boolean"));
  }

  private Model removeIsApplicable(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "isApplicable"));
  }

  private Model addIsApplicable(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :isApplicable ?addition .
        } WHERE {
          ?subject :isApplicable ?object .
          BIND (!?object as ?addition)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model addAbstract(Model model) {
    var query = addQuery(PUBLICATION, ABSTRACT_TEXT_PROPERTY, "Some abstract text");
    return addTriples(model, query);
  }

  private Model removeAbstract(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, ABSTRACT_TEXT_PROPERTY));
  }

  private Model addInvalidAbstract(Model model) {
    return addTriples(
        removeTriples(model, removeQuery(PUBLICATION, ABSTRACT_TEXT_PROPERTY)),
        addQuery(PUBLICATION, ABSTRACT_TEXT_PROPERTY, URI.create("https://example.com/abc")));
  }

  private Model repeatModifiedDate(Model model) {
    var query =
        """
        PREFIX : <%s>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        CONSTRUCT {
          ?subject :modifiedDate "%s"^^xsd:dateTime; .
        } WHERE {
          ?subject :modifiedDate ?object .
        }
        """
            .formatted(NVA_ONTOLOGY, getCurrentDateTime());
    return addTriples(model, query);
  }

  private Model replaceModifiedDateWithInvalidValue(Model model) {
    var removeTriples = removeTriples(model, removeQuery(PUBLICATION, "modifiedDate"));
    return addTriples(removeTriples, addQuery(PUBLICATION, "modifiedDate", "Not a date time"));
  }

  private static String getCurrentDateTime() {
    var now = Instant.now();
    var formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    return formatter.format(now);
  }

  private Model removeModifiedDate(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "modifiedDate"));
  }

  private Model addAdditionalPublicationIdentifier(Model model) {
    return addTriples(
        model, addQuery(PUBLICATION, IDENTIFIER_PROPERTY, UUID.randomUUID().toString()));
  }

  private Model replacePublicationIdentifierWithInvalidValue(Model model, Object value) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION, IDENTIFIER_PROPERTY));
    return addTriples(removeModel, addQuery(PUBLICATION, IDENTIFIER_PROPERTY, value));
  }

  private static Model addTriples(Model model, String query) {
    try (var queryExecution = QueryExecutionFactory.create(query, model)) {
      return model.add(queryExecution.execConstruct());
    }
  }

  private Model removePublicationIdentifier(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, IDENTIFIER_PROPERTY));
  }

  private static Model removeTriples(Model model, String query) {
    try (var queryExecution = QueryExecutionFactory.create(query, model)) {
      return model.remove(queryExecution.execConstruct());
    }
  }

  private Model removeContributors(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "contributor"));
  }

  private Model replaceContributorWithInvalidValue(Model model) {
    var removeModel = removeContributors(model);
    return addTriples(removeModel, addQuery(PUBLICATION, "contributor", "Herring stew"));
  }

  private Model createModelWithNoErrors() {
    var model = ModelFactory.createDefaultModel();
    var inputstream = IoUtils.inputStreamFromResources("errorfreeModel.nt");
    RDFDataMgr.read(model, inputstream, Lang.NTRIPLES);
    return model;
  }

  private Model addToModel(Model model, String data) {
    RDFDataMgr.read(model, new ByteArrayInputStream(data.getBytes(UTF_8)), Lang.TURTLE);
    return model;
  }

  private String removeQuery(String type, String property) {
    return """
           PREFIX : <%s>
           CONSTRUCT {
             ?subject :%s ?object .
             ?object ?b ?c .
           } WHERE {
             ?subject a :%s ;
                      :%s ?object .
             OPTIONAL { ?object ?b ?c }
           }
           """
        .formatted(NVA_ONTOLOGY, property, type, property);
  }

  private String additionalValueQuery(String type, String property, String addition) {
    return """
             PREFIX : <%s>
             CONSTRUCT {
               ?subject :%s :%s .
             } WHERE {
               ?subject a :%s ;
                        :%s [] .
             }
           """
        .formatted(NVA_ONTOLOGY, property, addition, type, property);
  }
}
