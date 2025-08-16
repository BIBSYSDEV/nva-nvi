package no.sikt.nva.nvi.common.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;

public class NvaGraphValidator implements GraphValidator {

  @Override
  public GraphValidation validate(Model model) {
    var shape = Shapes.parse(RDFDataMgr.loadGraph("nva-shape.ttl"));
    return new GraphValidation(ShaclValidator.get().validate(shape, model.getGraph()));
  }
}
