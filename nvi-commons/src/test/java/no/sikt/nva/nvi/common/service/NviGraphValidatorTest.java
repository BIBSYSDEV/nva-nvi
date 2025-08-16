package no.sikt.nva.nvi.common.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.logutils.LogUtils;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NviGraphValidatorTest {

  private static final String NVA_ONTOLOGY = "https://nva.sikt.no/ontology/publication#";
  public static final String PUBLICATION = "Publication";
  public static final String YEAR_PROPERTY = "year";
  public static final String PUBLICATION_DATE_CLASS = "PublicationDate";
  public static final String CONTRIBUTOR_CLASS = "Contributor";
  public static final String PUBLICATION_CHANNEL_CLASS = "PublicationChannel";
  private NviGraphValidator nviGraphValidator;
  private Logger logger;

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
        .hasSize(1);
  }

  @Test
  void shouldReportWhenPublicationInternationalCollaborationIsMissing() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(removeInternationalCollaboration(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication international collaboration is not flagged")
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
  void shouldReportWhenPublicationLanguageIsRepeated() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(addILanguage(model));
    assertThat(validation.generateReport())
        .containsSequence("Publication language is repeated")
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
    var modelWithTwoChannels = addIPublicationChannel(model);
    var modelWithThreeChannels = addIPublicationChannel(modelWithTwoChannels);
    var validation = nviGraphValidator.validate(modelWithThreeChannels);
    assertThat(validation.generateReport())
        .containsSequence("Publication channel is repeated more than twice")
        .hasSize(1);
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
        // duplicates), we have
        // two errors.
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

  @Test
  void shouldReportWhenPublicationChannelScientificValueIsInvalid() {
    var model = createModelWithNoErrors();
    var validation = nviGraphValidator.validate(replacePublicationChannelScientificValue(model));
    assertThat(validation.generateReport())
        .containsSequence(
            "Publication channel scientific value is not a string matching (LevelOne, LevelTwo)")
        .hasSize(1);
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
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :year "MMXXV" .
        } WHERE {
          ?subject a :PublicationDate .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removeModel, query);
  }

  private Model removePublicationDateYear(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_DATE_CLASS, YEAR_PROPERTY));
  }

  private Model addPublicationDateYear(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :year "7777" .
        } WHERE {
          ?subject a :PublicationDate ;
                   :year [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
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
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :channelType :NonsenseChannelType .
        } WHERE {
          ?subject a :PublicationChannel .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removeModel, query);
  }

  private Model removePublicationChannelName(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "name"));
  }

  private Model addPublicationChannelName(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :name "Another name for the thing" .
        } WHERE {
          ?subject a :PublicationChannel ;
                   :name [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replacePublicationChannelName(Model model) {
    var removeModel = removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "name"));
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :name <https://example.org/abc> .
        } WHERE {
          ?subject a :PublicationChannel .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removeModel, query);
  }

  private Model removePublicationChannelIdentifier(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "identifier"));
  }

  private Model addPublicationChannelIdentifier(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :identifier "%s" .
        } WHERE {
          ?subject a :PublicationChannel ;
                   :identifier [] .
        }
        """
            .formatted(NVA_ONTOLOGY, UUID.randomUUID().toString());
    return addTriples(model, query);
  }

  private Model replacePublicationChannelIdentifier(Model model) {
    var removedModel = removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "identifier"));
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :identifier :NonsensicalIdentifier .
        } WHERE {
          ?subject a :PublicationChannel .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removedModel, query);
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

  private Model replacePublicationChannelScientificValue(Model model) {
    var removedModel =
        removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "scientificValue"));
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :scientificValue "NonsenseLevel" .
        } WHERE {
          ?subject a :PublicationChannel .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removedModel, query);
  }

  private Model replacePublicationChannelYear(Model model) {
    var removed = removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, YEAR_PROPERTY));
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :year "MMXXV" .
        } WHERE {
          ?subject a :PublicationChannel .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removed, query);
  }

  private Model addPublicationChannelYear(Model model) {
    String query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :year "1777" .
        } WHERE {
          ?subject a :PublicationChannel ;
                   :year [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model removePublicationChannelYear(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, YEAR_PROPERTY));
  }

  private Model removePublicationChannelIssn(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "printIssn"));
  }

  private Model addPublicationChannelIssn(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :printIssn "2999-111X" .
        } WHERE {
          ?subject a :PublicationChannel ;
                   :printIssn [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replacePublicationChannelIssnWithInvalidValue(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :printIssn "2999-111GUM" .
        } WHERE {
          ?subject a :PublicationChannel .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(
        removeTriples(model, removeQuery(PUBLICATION_CHANNEL_CLASS, "printIssn")), query);
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
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject  :verificationStatus "Longshoreperson" .
        } WHERE {
          ?subject a :Contributor .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(
        removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "verificationStatus")), query);
  }

  private Model removeContributorRole(Model model) {
    return removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "role"));
  }

  private Model addContributorRole(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :role :Photographer .
        } WHERE {
          ?subject a :Contributor ;
                  :role [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replaceContributorRoleWithInvalidValue(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :role :Photographer .
        } WHERE {
          ?subject a :Contributor .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "role")), query);
  }

  private Model replaceContributorNameWithInvalidValue(Model model) {
    var addQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :name 222 .
        } WHERE {
          ?subject a :Contributor ;
                   :name [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    var removeQuery =
        """
        PREFIX : <%s>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        CONSTRUCT {
          ?subject :name ?object .
        } WHERE {
          ?subject a :Contributor ;
                   :name ?object .
          FILTER(DATATYPE(?object) = xsd:string)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return removeTriples(addTriples(model, addQuery), removeQuery);
  }

  private Model addContributorName(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :name "Hockey McWilson" .
        } WHERE {
          ?subject a :Contributor ;
                   :name [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model removeContributorName(Model model) {
    return removeTriples(model, removeQuery(CONTRIBUTOR_CLASS, "name"));
  }

  private Model replaceContributorAffiliationWithInvalidData(Model model) {
    var replacement = "Milky way galaxy";
    var addQuery =
        """
          PREFIX : <%s>
          CONSTRUCT {
            ?subject :affiliation "%s" .
          } WHERE {
            ?subject a :Contributor ;
                     :affiliation [] .
          }
        """
            .formatted(NVA_ONTOLOGY, replacement);
    var removeQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :affiliation ?object
        } WHERE {
          ?subject a :Contributor ;
                   :affiliation ?object .
          FILTER(?object != "%s")
        }
        """
            .formatted(NVA_ONTOLOGY, replacement);
    return removeTriples(addTriples(model, addQuery), removeQuery);
  }

  private Model removeContributorAffiliation(Model model) {
    var query = removeQuery(CONTRIBUTOR_CLASS, "affiliation");
    return removeTriples(model, query);
  }

  private Model removePublicationTopLevelOrganization(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "topLevelOrganization"));
  }

  private Model replacePublicationTopLevelOrganizationWithInvalidType(Model model) {
    var organization = "\"A string not an organization\"";
    var addQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :topLevelOrganization %s .
        } WHERE {
          ?subject a :Publication ;
                   :topLevelOrganization [] .
        }
        """
            .formatted(NVA_ONTOLOGY, organization);
    var removeQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :topLevelOrganization ?organization .
        } WHERE {
          ?subject a :Publication ;
                   :topLevelOrganization ?organization .
          FILTER(?organization != %s)
        }
        """
            .formatted(NVA_ONTOLOGY, organization);
    return removeTriples(addTriples(model, addQuery), removeQuery);
  }

  private Model removePublicationTitle(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "title"));
  }

  private Model addPublicationTitle(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
         ?subject :title "Some additional title" .
        } WHERE {
          ?subject a :Publication ;
                   :title [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model replacePublicationTitleWithInvalidType(Model model) {
    var addQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :title <https://example.org/not-a-title> .
        } WHERE {
          ?subject a :Publication ;
                  :title [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    var removeQuery =
        """
        PREFIX : <%s>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        CONSTRUCT {
          ?subject :title ?title .
        } WHERE {
          ?subject a :Publication ;
                   :title ?title .
          FILTER(DATATYPE(?title) = xsd:string)
        }
        """
            .formatted(NVA_ONTOLOGY);
    return removeTriples(addTriples(model, addQuery), removeQuery);
  }

  private Model addPublicationStatus(Model model) {
    return addTriples(model, additionalValueQuery(PUBLICATION, "status", "UNPUBLISHED"));
  }

  private Model removePublicationStatus(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "status"));
  }

  private Model replacePublicationStatusWithInvalidType(Model model) {
    var status = ":NONSENSE_STATUS";
    var addQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :status %s .
        } WHERE {
          ?subject :status [] .
        }
        """
            .formatted(NVA_ONTOLOGY, status);
    var removeQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :status ?object .
        } WHERE {
          ?subject :status ?object .
          FILTER(?object != %s)
        }
        """
            .formatted(NVA_ONTOLOGY, status);
    return removeTriples(addTriples(model, addQuery), removeQuery);
  }

  private Model removePublicationType(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "publicationType"));
  }

  private Model addPublicationType(Model model) {
    return addTriples(
        model, additionalValueQuery(PUBLICATION, "publicationType", "AcademicMonograph"));
  }

  private Model replacePublicationTypeWithInvalidType(Model model) {
    var type = ":NonsenseCategory";
    var addQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :publicationType %s .
        } WHERE {
          ?subject :publicationType [] .
        }
        """
            .formatted(NVA_ONTOLOGY, type);
    var removeQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :publicationType ?object .
        } WHERE {
          ?subject :publicationType ?object .
          FILTER(?object != %s)
        }
        """
            .formatted(NVA_ONTOLOGY, type);
    return removeTriples(addTriples(model, addQuery), removeQuery);
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

  private Model swapPublicationChannelWithInvalidIri(Model model) {
    var removeQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :publicationChannel ?object .
        } WHERE {
          ?subject :publicationChannel ?object .
          FILTER(REGEX(STR(?object), ".*unit.*", "i"))
        }
        """
            .formatted(NVA_ONTOLOGY);
    var addQuery =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :publicationChannel <https://example.org/some/fishy/iri> .
        } WHERE {
          ?subject :publicationChannel ?object .
        }
        """
            .formatted(NVA_ONTOLOGY);
    var modelAdd = addTriples(model, addQuery);
    return removeTriples(modelAdd, removeQuery);
  }

  private Model removePublicationChannel(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "publicationChannel"));
  }

  private Model addIPublicationChannel(Model model) {
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
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :language <http://lexvo.org/id/iso639-3/cym> .
        } WHERE {
          ?subject :language ?object .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
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
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :abstractText "Some more abstract text" .
        } WHERE {
          ?subject :abstractText [] .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return addTriples(model, query);
  }

  private Model removeAbstract(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "abstractText"));
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
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :identifier "%s" .
        } WHERE {
          ?subject a :Publication .
        }
        """
            .formatted(NVA_ONTOLOGY, UUID.randomUUID().toString());
    return addTriples(model, query);
  }

  private static Model addTriples(Model model, String query) {
    try (var queryExecution = QueryExecutionFactory.create(query, model)) {
      return model.add(queryExecution.execConstruct());
    }
  }

  private Model removePublicationIdentifier(Model model) {
    var query =
        """
        PREFIX : <%s>
        CONSTRUCT {
          ?subject :identifier ?object .
        } WHERE {
          ?subject a :Publication ;
                   :identifier ?object .
        }
        """
            .formatted(NVA_ONTOLOGY);
    return removeTriples(model, query);
  }

  private static Model removeTriples(Model model, String query) {
    try (var queryExecution = QueryExecutionFactory.create(query, model)) {
      return model.remove(queryExecution.execConstruct());
    }
  }

  private Model removeContributors(Model model) {
    return removeTriples(model, removeQuery(PUBLICATION, "contributor"));
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
