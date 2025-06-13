package no.sikt.nva.nvi.common.permissions;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.permissions.deny.DenyStrategy;
import no.sikt.nva.nvi.common.permissions.deny.ReportStatusDenyStrategy;
import no.sikt.nva.nvi.common.permissions.grant.GrantStrategy;
import no.sikt.nva.nvi.common.permissions.grant.NviAdminGrantStrategy;
import no.sikt.nva.nvi.common.permissions.grant.NviCuratorGrantStrategy;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.model.Candidate;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CandidatePermissions {
  private static final Logger logger = LoggerFactory.getLogger(CandidatePermissions.class);
  private static final String COMMA_DELIMITER = ", ";
  private final Candidate candidate;
  private final UserInstance userInstance;
  private final Set<GrantStrategy> grantStrategies;
  private final Set<DenyStrategy> denyStrategies;

  public CandidatePermissions(Candidate candidate, UserInstance userInstance) {
    this.candidate = candidate;
    this.userInstance = userInstance;
    this.grantStrategies =
        Set.of(
            new NviAdminGrantStrategy(candidate, userInstance),
            new NviCuratorGrantStrategy(candidate, userInstance));
    this.denyStrategies = Set.of(new ReportStatusDenyStrategy(candidate, userInstance));
  }

  public static CandidatePermissions create(Candidate candidate, UserInstance userInstance) {
    return new CandidatePermissions(candidate, userInstance);
  }

  public boolean allowsAction(CandidateOperation operation) {
    return isGrantedPermission(operation) && !isDeniedPermission(operation);
  }

  public Set<CandidateOperation> getAllAllowedActions() {
    return Arrays.stream(CandidateOperation.values())
        .filter(this::allowsAction)
        .collect(Collectors.toSet());
  }

  public void validateAuthorization(CandidateOperation operation) throws UnauthorizedException {
    validateDenyStrategiesRestrictions(operation);
    validateGrantStrategies(operation);
  }

  private boolean isGrantedPermission(CandidateOperation operation) {
    return grantStrategies.stream().anyMatch(strategy -> strategy.allowsAction(operation));
  }

  private boolean isDeniedPermission(CandidateOperation operation) {
    return denyStrategies.stream().anyMatch(strategy -> strategy.deniesAction(operation));
  }

  private List<GrantStrategy> findAllowances(CandidateOperation operation) {
    return grantStrategies.stream().filter(strategy -> strategy.allowsAction(operation)).toList();
  }

  private List<DenyStrategy> findDenials(CandidateOperation operation) {
    return denyStrategies.stream().filter(strategy -> strategy.deniesAction(operation)).toList();
  }

  private void validateDenyStrategiesRestrictions(CandidateOperation operation)
      throws UnauthorizedException {
    var strategies =
        findDenials(operation).stream()
            .map(DenyStrategy::getClass)
            .map(Class::getSimpleName)
            .toList();

    if (!strategies.isEmpty()) {
      logger.info(
          "User {} was denied access {} on candidate {} from strategies {}",
          userInstance.userName(),
          operation,
          candidate.getIdentifier(),
          String.join(COMMA_DELIMITER, strategies));

      throw new UnauthorizedException(formatUnauthorizedMessage(operation));
    }
  }

  private void validateGrantStrategies(CandidateOperation operation) throws UnauthorizedException {
    var strategies =
        findAllowances(operation).stream()
            .map(GrantStrategy::getClass)
            .map(Class::getSimpleName)
            .toList();

    if (strategies.isEmpty()) {
      throw new UnauthorizedException(formatUnauthorizedMessage(operation));
    }

    logger.info(
        "User {} was allowed {} on candidate {} from strategies {}",
        userInstance.userName(),
        operation,
        candidate.getIdentifier(),
        String.join(COMMA_DELIMITER, strategies));
  }

  private String formatUnauthorizedMessage(CandidateOperation operation) {
    return String.format(
        "Unauthorized: %s is not allowed to perform %s on %s",
        userInstance.userName(), operation, candidate.getIdentifier());
  }
}
