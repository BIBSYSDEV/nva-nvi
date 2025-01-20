package no.sikt.nva.nvi.events.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.net.URI;

public record Channel(URI id, PublicationChannel type, @JsonAlias("scientificValue") Level level) {}
