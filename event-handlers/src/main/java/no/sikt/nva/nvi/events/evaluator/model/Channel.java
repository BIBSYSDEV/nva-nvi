package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import no.sikt.nva.nvi.common.model.ScientificValue;

public record Channel(URI id, PublicationChannel type, ScientificValue scientificValue) {}
