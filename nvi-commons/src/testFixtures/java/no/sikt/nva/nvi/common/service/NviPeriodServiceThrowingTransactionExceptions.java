package no.sikt.nva.nvi.common.service;

import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.exceptions.TransactionException;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import nva.commons.core.Environment;

public class NviPeriodServiceThrowingTransactionExceptions extends NviPeriodService {

  public NviPeriodServiceThrowingTransactionExceptions(
      Environment environment, PeriodRepository periodRepository) {
    super(environment, periodRepository);
  }

  @Override
  public void update(UpdatePeriodRequest request) {
    throw new TransactionException("Fake failure simulating a concurrent update");
  }
}
