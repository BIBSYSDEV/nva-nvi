package no.sikt.nva.nvi.rdf;

@FunctionalInterface
public interface GraphValidable {

  GraphValidation validate(GraphValidator validator);
}
