package no.sikt.nva.nvi.common.service;

@FunctionalInterface
public interface GraphValidable {

  GraphValidation validate(GraphValidator validator);
}
