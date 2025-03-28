package no.sikt.nva.nvi.events.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.net.URI;
import no.sikt.nva.nvi.common.model.ScientificValue;

public record Channel(
    URI id, PublicationChannel type, @JsonAlias("scientificValue") ScientificValue level) {}
