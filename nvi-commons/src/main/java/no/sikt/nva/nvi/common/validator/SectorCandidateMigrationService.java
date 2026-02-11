package no.sikt.nva.nvi.common.validator;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.service.CandidateService.defaultCandidateService;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.CandidateMigrationService;
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

public class SectorCandidateMigrationService implements CandidateMigrationService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SectorCandidateMigrationService.class);
  private final CandidateService candidateService;
  private final IdentityServiceClient identityServiceClient;
  private final List<CustomerDto> customers = new ArrayList<>();

  public SectorCandidateMigrationService(
      CandidateService candidateService, IdentityServiceClient identityServiceClient) {
    this.candidateService = candidateService;
    this.identityServiceClient = identityServiceClient;
  }

  @JacocoGenerated
  public static SectorCandidateMigrationService defaultService() {
    return new SectorCandidateMigrationService(
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

  private List<CustomerDto> getCustomers() {
    return customers.isEmpty() ? fetchCustomers() : customers;
  }

  private List<CustomerDto> fetchCustomers() {
    var fetchedCustomers =
        attempt(identityServiceClient::getAllCustomers).map(CustomerList::customers).orElseThrow();
    customers.addAll(fetchedCustomers);
    return customers;
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
    return getCustomers().stream()
        .filter(customerDto -> customerDto.cristinId().equals(institutionId))
        .findFirst()
        .map(CustomerDto::sector)
        .flatMap(Sector::fromString)
        .orElseThrow();
  }

  private boolean shouldMigrateInstitutionPointMissingSector(Candidate candidate) {
    return candidate.pointCalculation().institutionPoints().stream()
        .anyMatch(this::sectorDifferFromCustomerSector);
  }

  private boolean sectorDifferFromCustomerSector(InstitutionPoints institutionPoints) {
    return isNull(institutionPoints.sector())
        || !institutionPoints
            .sector()
            .equals(getInstitutionSector(institutionPoints.institutionId()));
  }
}
