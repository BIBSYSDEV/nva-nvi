package no.sikt.nva.nvi.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.junit.jupiter.api.Test;

class GraphTest {

  @Test
  void shouldMergeSourcesIntoOneGraphWithoutMutatingThem() {
    var firstSource = parse("@prefix : <http://example.org/> . :a :p :b .");
    var secondSource = parse("@prefix : <http://example.org/> . :c :p :d .");
    var mergedSize = new AtomicLong();

    Graph.of(firstSource).add(secondSource).validate(model -> conforming(model, mergedSize));

    assertEquals(2, mergedSize.get());
    assertEquals(1, firstSource.size());
    assertEquals(1, secondSource.size());
  }

  private static GraphValidation conforming(Model model, AtomicLong observedSize) {
    observedSize.set(model.size());
    var noShapes = Shapes.parse(ModelFactory.createDefaultModel().getGraph());
    return new GraphValidation(ShaclValidator.get().validate(noShapes, model.getGraph()));
  }

  private static Model parse(String turtle) {
    var model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, new ByteArrayInputStream(turtle.getBytes(UTF_8)), Lang.TURTLE);
    return model;
  }
}
