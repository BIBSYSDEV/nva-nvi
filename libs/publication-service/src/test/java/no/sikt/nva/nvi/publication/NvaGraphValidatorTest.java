package no.sikt.nva.nvi.publication;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for the NVA input shape. The NVI shape is covered by NviGraphValidatorTest. */
class NvaGraphValidatorTest {

  private static final String NVA_ONTOLOGY = "https://nva.sikt.no/ontology/publication#";
  private static final String PREFIX = "@prefix : <%s> .%n".formatted(NVA_ONTOLOGY);
  private static final String PUBLICATION_WITH_ONE_CONTRIBUTOR =
      """
      :publication a :Publication ;
                   :entityDescription :entityDescription .
      :entityDescription :contributor :contributor .
      :contributor a :Contributor ;
                   :identity :person ;
                   :role :role ;
                   :affiliation :organization .
      :role a :Creator .
      :person a :Identity ;
              :name "Ola Nordmann" ;
              :verificationStatus "Verified" .
      """;
  private NvaGraphValidator nvaGraphValidator;

  @BeforeEach
  void setUp() {
    nvaGraphValidator = new NvaGraphValidator();
  }

  @Test
  void shouldNotReportValidContributor() {
    var validation = nvaGraphValidator.validate(createModel());

    assertThat(validation.generateReport()).isEmpty();
  }

  @Test
  void shouldReportWhenContributorIdentityIsMissing() {
    var model = createModel();
    model.removeAll(resource(model, "contributor"), property(model, "identity"), null);

    var validation = nvaGraphValidator.validate(model);

    assertThat(validation.generateReport()).containsExactly("Contributor identity is missing");
  }

  @Test
  void shouldReportWhenContributorIdentityIsRepeated() {
    var model = addToModel(createModel(), ":contributor :identity :otherPerson .");

    var validation = nvaGraphValidator.validate(model);

    assertThat(validation.generateReport()).containsExactly("Contributor identity is repeated");
  }

  @Test
  void shouldReportWhenContributorRoleTypeIsMissing() {
    var model = createModel();
    model.removeAll(resource(model, "role"), RDF.type, null);

    var validation = nvaGraphValidator.validate(model);

    assertThat(validation.generateReport()).containsExactly("Contributor role type is missing");
  }

  @Test
  void shouldReportWhenContributorRoleHasMultipleTypes() {
    var model = addToModel(createModel(), ":role a :Editor .");

    var validation = nvaGraphValidator.validate(model);

    assertThat(validation.generateReport()).containsExactly("Contributor role has multiple types");
  }

  @Test
  void shouldNotValidateContributorsPreview() {
    var previewContributor =
        """
        :entityDescription :contributorsPreview :previewContributor .
        :previewContributor a :Contributor ;
                            :role :previewRole .
        :previewRole a :Creator , :Editor .
        """;
    var model = addToModel(createModel(), previewContributor);

    var validation = nvaGraphValidator.validate(model);

    assertThat(validation.generateReport()).isEmpty();
  }

  private static Model createModel() {
    return addToModel(ModelFactory.createDefaultModel(), PUBLICATION_WITH_ONE_CONTRIBUTOR);
  }

  private static Model addToModel(Model model, String turtle) {
    var document = PREFIX + turtle;
    RDFDataMgr.read(model, new ByteArrayInputStream(document.getBytes(UTF_8)), Lang.TURTLE);
    return model;
  }

  private static Resource resource(Model model, String localName) {
    return model.createResource(NVA_ONTOLOGY + localName);
  }

  private static Property property(Model model, String localName) {
    return model.createProperty(NVA_ONTOLOGY + localName);
  }
}
