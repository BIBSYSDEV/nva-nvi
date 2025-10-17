package no.sikt.nva.nvi.common.service.model;

import java.util.Optional;

public record NoteCreationResult(Note note, Optional<Approval> updatedApproval) {}
