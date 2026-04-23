package no.sikt.nva.nvi.index.model;

import java.util.UUID;
import no.unit.nva.commons.json.JsonSerializable;

public record IndexCandidateMessage(UUID candidateIdentifier) implements JsonSerializable {}
