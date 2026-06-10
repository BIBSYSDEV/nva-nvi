package no.sikt.nva.nvi.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import no.sikt.nva.nvi.rdf.SparqlConstruct;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the applicability rules in nvi_applicability.rq at the query level: a series with an
 * assigned scientific value decides applicability and overrides other channels, and only when no
 * such series exists do other channels count. Each case asserts exactly one isApplicable triple, so
 * conflicting true/false values from repeated bindings cannot slip through unnoticed.
 */
class NviProjectionApplicabilityTest {

  private static final SparqlConstruct NVI_PROJECTION =
      SparqlConstruct.fromResource("nvi_applicability.rq");

  private static final String APPLICABLE_PUBLICATION_BASE =
      """
      @prefix : <https://nva.sikt.no/ontology/publication#> .
      :pub a :Publication ;
           :status :PUBLISHED ;
           :entityDescription :entityDesc .
      :entityDesc :reference :ref .
      :ref a :Reference ;
           :publicationInstance :instance .
      :instance a :AcademicMonograph .
      """;

  static Stream<Arguments> applicabilityCases() {
    return Stream.of(
        argumentSet(
            "series with applicable level",
            """
            :series a :Series ; :scientificValue "LevelOne" .
            """,
            true),
        argumentSet(
            "unassigned series falls back to applicable publisher",
            """
            :series a :Series ; :scientificValue "Unassigned" .
            :publisher a :Publisher ; :scientificValue "LevelOne" .
            """,
            true),
        argumentSet(
            "no series falls back to applicable publisher",
            """
            :publisher a :Publisher ; :scientificValue "LevelOne" .
            """,
            true),
        argumentSet(
            "series with non-applicable level overrides applicable publisher",
            """
            :series a :Series ; :scientificValue "LevelZero" .
            :publisher a :Publisher ; :scientificValue "LevelOne" .
            """,
            false),
        argumentSet(
            "unassigned series with non-applicable publisher",
            """
            :series a :Series ; :scientificValue "Unassigned" .
            :publisher a :Publisher ; :scientificValue "LevelZero" .
            """,
            false),
        argumentSet("no channel with scientific value", ":publisher a :Publisher .", false),
        argumentSet(
            "applicable level on a non-channel node is ignored",
            """
            :somethingElse a :Organization ; :scientificValue "LevelOne" .
            """,
            false),
        argumentSet(
            "two series with conflicting levels yield a single positive flag",
            """
            :seriesOne a :Series ; :scientificValue "LevelZero" .
            :seriesTwo a :Series ; :scientificValue "LevelOne" .
            """,
            true),
        argumentSet(
            "revised publication is not applicable despite applicable series",
            """
            :series a :Series ; :scientificValue "LevelOne" .
            :ref :publicationContext :context .
            :context :revision "Revised" .
            """,
            false));
  }

  @ParameterizedTest
  @MethodSource("applicabilityCases")
  void shouldDeriveApplicabilityFromSeriesWithChannelFallback(
      String channelTriples, boolean expectedApplicability) {
    var applicabilityFlags =
        projectApplicabilityFlags(APPLICABLE_PUBLICATION_BASE + channelTriples);

    assertThat(applicabilityFlags)
        .hasSize(1)
        .allSatisfy(
            flag -> assertThat(flag.asLiteral().getBoolean()).isEqualTo(expectedApplicability));
  }

  @Test
  void shouldExplicitlyNotBeApplicableWhenPublicationIsNotPublished() {
    var draftPublication = APPLICABLE_PUBLICATION_BASE.replace(":PUBLISHED", ":DRAFT");
    var applicabilityFlags =
        projectApplicabilityFlags(
            draftPublication + ":series a :Series ; :scientificValue \"LevelOne\" .");

    assertThat(applicabilityFlags)
        .hasSize(1)
        .allSatisfy(flag -> assertThat(flag.asLiteral().getBoolean()).isFalse());
  }

  private List<RDFNode> projectApplicabilityFlags(String turtle) {
    var model = ModelFactory.createDefaultModel();
    model.read(new StringReader(turtle), null, "TTL");
    var projected = NVI_PROJECTION.projectFrom(model);
    var applicabilityFlags = new ArrayList<RDFNode>();
    projected
        .listStatements()
        .filterKeep(statement -> "isApplicable".equals(statement.getPredicate().getLocalName()))
        .forEachRemaining(statement -> applicabilityFlags.add(statement.getObject()));
    return applicabilityFlags;
  }
}
