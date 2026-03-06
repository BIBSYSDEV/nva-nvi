package no.sikt.nva.nvi.common.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;

public class NviGraphValidator implements GraphValidator {

  private static final String NVI_SHAPE_TTL = "nvi-shape.ttl";
  private static final Shapes NVI_SHAPE = Shapes.parse(RDFDataMgr.loadGraph(NVI_SHAPE_TTL));

  @Override
  public GraphValidation validate(Model model) {
    return new GraphValidation(ShaclValidator.get().validate(NVI_SHAPE, model.getGraph()));
  }
}
