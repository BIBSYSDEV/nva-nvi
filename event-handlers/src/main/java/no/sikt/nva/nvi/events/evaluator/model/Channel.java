package no.sikt.nva.nvi.events.evaluator.model;

import java.net.URI;
import no.sikt.nva.nvi.common.model.ScientificValue;

// FIXME: Remove this duplicate class
public record Channel(URI id, PublicationChannel type, ScientificValue scientificValue) {}
