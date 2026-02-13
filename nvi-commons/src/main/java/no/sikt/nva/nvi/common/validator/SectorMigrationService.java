package no.sikt.nva.nvi.common.validator;

import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.MigrationService;
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorMigrationService implements MigrationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SectorMigrationService.class);
  private final CandidateService candidateService;
  private final IdentityServiceClient identityServiceClient;
  private final Map<URI, CustomerDto> customers;

  public SectorMigrationService(
      CandidateService candidateService, IdentityServiceClient identityServiceClient) {
    this.candidateService = candidateService;
    this.identityServiceClient = identityServiceClient;
    this.customers = fetchCustomers();
  }

  @JacocoGenerated
  public static SectorMigrationService defaultService() {
    return new SectorMigrationService(
        defaultCandidateService(), IdentityServiceClient.unauthorizedIdentityServiceClient());
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    if (shouldMigrateInstitutionPointMissingSector(candidate)) {
      var enrichedCandidate = addMissingSector(candidate);
      updateCandidateIfNeeded(enrichedCandidate, candidate);
      LOGGER.info("Candidate {} migrated", identifier);
    }
  }

  private void updateCandidateIfNeeded(Candidate enrichedCandidate, Candidate candidate) {
    if (!enrichedCandidate.equals(candidate)) {
      candidateService.updateCandidate(enrichedCandidate);
    }
  }

  private Candidate addMissingSector(Candidate candidate) {
    return candidate
        .copy()
        .withPointCalculation(migratePointCalculation(candidate.pointCalculation()))
        .build();
  }

  private Map<URI, CustomerDto> fetchCustomers() {
    return attempt(identityServiceClient::getAllCustomers)
        .map(CustomerList::customers)
        .map(SectorMigrationService::collectToCustomerMap)
        .orElseThrow(
            failure -> new RuntimeException("Could not fetch customers!", failure.getException()));
  }

  private static Map<URI, CustomerDto> collectToCustomerMap(List<CustomerDto> list) {
    return list.stream().collect(Collectors.toMap(CustomerDto::cristinId, Function.identity()));
  }

  private PointCalculation migratePointCalculation(PointCalculation pointCalculation) {
    return new PointCalculation(
        pointCalculation.instanceType(),
        pointCalculation.channel(),
        pointCalculation.isInternationalCollaboration(),
        pointCalculation.collaborationFactor(),
        pointCalculation.basePoints(),
        pointCalculation.creatorShareCount(),
        migrateInstitutionPoints(pointCalculation.institutionPoints()),
        pointCalculation.totalPoints());
  }

  private Collection<InstitutionPoints> migrateInstitutionPoints(
      Collection<InstitutionPoints> institutionPoints) {
    return institutionPoints.stream().map(this::migrateInstitutionPoint).toList();
  }

  private InstitutionPoints migrateInstitutionPoint(InstitutionPoints institutionPoints) {
    var sector = getInstitutionSector(institutionPoints.institutionId());
    return new InstitutionPoints(
        institutionPoints.institutionId(),
        institutionPoints.institutionPoints(),
        sectorDifferFromCustomerSector(institutionPoints) ? sector : institutionPoints.sector(),
        institutionPoints.creatorAffiliationPoints());
  }

  private Sector getInstitutionSector(URI institutionId) {
    return Optional.ofNullable(customers.get(institutionId))
        .map(CustomerDto::sector)
        .flatMap(Sector::fromString)
        .orElseThrow();
  }

  private boolean shouldMigrateInstitutionPointMissingSector(Candidate candidate) {
    return candidate.pointCalculation().institutionPoints().stream()
        .anyMatch(this::sectorDifferFromCustomerSector);
  }

  private boolean sectorDifferFromCustomerSector(InstitutionPoints institutionPoints) {
    var customerSector = getInstitutionSector(institutionPoints.institutionId());
    return institutionPoints.sector() != customerSector;
  }
}
