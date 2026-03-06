package no.sikt.nva.nvi.common.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;

public class NvaGraphValidator implements GraphValidator {

  private static final String NVA_SHAPE_TTL = "nva-shape.ttl";
  private static final Shapes NVA_SHAPE = Shapes.parse(RDFDataMgr.loadGraph(NVA_SHAPE_TTL));

  @Override
  public GraphValidation validate(Model model) {
    return new GraphValidation(ShaclValidator.get().validate(NVA_SHAPE, model.getGraph()));
  }
}
