package no.sikt.nva.nvi.common;

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
import no.sikt.nva.nvi.common.model.PointCalculation;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.unit.nva.clients.CustomerDto;
import no.unit.nva.clients.CustomerList;
import no.unit.nva.clients.IdentityServiceClient;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Inactive migration code that should be removed.
 */
@Deprecated(forRemoval = true)
public class RboInstitutionMigrationService implements MigrationService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RboInstitutionMigrationService.class);
  private final CandidateService candidateService;
  private final Map<URI, CustomerDto> customers;

  public RboInstitutionMigrationService(
      CandidateService candidateService, IdentityServiceClient identityServiceClient) {
    this.candidateService = candidateService;
    this.customers = fetchCustomers(identityServiceClient);
  }

  @JacocoGenerated
  public static RboInstitutionMigrationService defaultService() {
    return new RboInstitutionMigrationService(
        defaultCandidateService(), IdentityServiceClient.unauthorizedIdentityServiceClient());
  }

  @Override
  public void migrateCandidate(UUID identifier) {
    var candidate = candidateService.getCandidateByIdentifier(identifier);
    if (shouldMigrate(candidate)) {
      var enrichedCandidate = addMissingRboInstitution(candidate);
      updateCandidateIfNeeded(enrichedCandidate, candidate);
      LOGGER.info("Candidate {} migrated with rboInstitution", identifier);
    }
  }

  private void updateCandidateIfNeeded(Candidate enrichedCandidate, Candidate candidate) {
    if (!enrichedCandidate.equals(candidate)) {
      candidateService.updateCandidate(enrichedCandidate);
    }
  }

  private Candidate addMissingRboInstitution(Candidate candidate) {
    return candidate
        .copy()
        .withPointCalculation(migratePointCalculation(candidate.pointCalculation()))
        .build();
  }

  private Map<URI, CustomerDto> fetchCustomers(IdentityServiceClient identityServiceClient) {
    return attempt(identityServiceClient::getAllCustomers)
        .map(CustomerList::customers)
        .map(RboInstitutionMigrationService::collectToCustomerMap)
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
    var rboInstitution = getCustomerRboInstitution(institutionPoints.institutionId());
    return new InstitutionPoints(
        institutionPoints.institutionId(),
        institutionPoints.institutionPoints(),
        institutionPoints.sector(),
        rboInstitution,
        institutionPoints.creatorAffiliationPoints());
  }

  private boolean getCustomerRboInstitution(URI institutionId) {
    return Optional.ofNullable(customers.get(institutionId))
        .map(CustomerDto::rboInstitution)
        .orElse(false);
  }

  private boolean shouldMigrate(Candidate candidate) {
    return candidate.pointCalculation().institutionPoints().stream()
        .anyMatch(this::rboInstitutionDiffersFromCustomer);
  }

  private boolean rboInstitutionDiffersFromCustomer(InstitutionPoints institutionPoints) {
    var customerRbo = getCustomerRboInstitution(institutionPoints.institutionId());
    return institutionPoints.rboInstitution() != customerRbo;
  }
}
