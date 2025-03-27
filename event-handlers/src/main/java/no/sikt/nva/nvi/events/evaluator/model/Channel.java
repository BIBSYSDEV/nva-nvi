package no.sikt.nva.nvi.events.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.net.URI;
import no.sikt.nva.nvi.common.dto.ScientificValue;

public record Channel(
    URI id, PublicationChannel type, @JsonAlias("scientificValue") ScientificValue level) {}
