package no.sikt.nva.nvi.rest.model;

import java.net.URI;

public record UpsertAssigneeRequest(String assignee, URI institutionId) {}
