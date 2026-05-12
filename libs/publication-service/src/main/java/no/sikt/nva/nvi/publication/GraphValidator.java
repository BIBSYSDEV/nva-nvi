package no.sikt.nva.nvi.publication;

import org.apache.jena.rdf.model.Model;

@FunctionalInterface
public interface GraphValidator {

  GraphValidation validate(Model model);
}
