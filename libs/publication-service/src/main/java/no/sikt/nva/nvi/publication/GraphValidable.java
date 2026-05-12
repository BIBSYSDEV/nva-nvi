package no.sikt.nva.nvi.publication;

@FunctionalInterface
public interface GraphValidable {

  GraphValidation validate(GraphValidator validator);
}
