package no.sikt.nva.nvi.common.service;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.exception.PeriodAlreadyExistsException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.common.service.model.UpdatePeriodRequest;
import no.sikt.nva.nvi.common.service.requests.CreatePeriodRequest;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NviPeriodService {

  private static final Logger LOGGER = LoggerFactory.getLogger(NviPeriodService.class);
  private static final String API_HOST = "API_HOST";
  private static final String PERIOD_PATH = "period";
  private static final String SCIENTIFIC_INDEX_API_PATH = "scientific-index";
  private final Environment environment;
  private final PeriodRepository periodRepository;

  public NviPeriodService(Environment environment, PeriodRepository periodRepository) {
    this.environment = environment;
    this.periodRepository = periodRepository;
  }

  public void create(CreatePeriodRequest request) {
    LOGGER.info("Processing create period request {}", request);
    request.validate();
    var year = String.valueOf(request.publishingYear());
    if (findByPublishingYear(year).isPresent()) {
      throw PeriodAlreadyExistsException.forYear(year);
    }

    var updatedPeriod = createNewPeriodFromRequest(request).toDao();
    periodRepository.save(updatedPeriod);
    LOGGER.info("Created new period successfully");
  }

  public void update(UpdatePeriodRequest request) {
    LOGGER.info("Processing update period request {}", request);
    request.validate();
    var year = String.valueOf(request.publishingYear());
    var currentPeriod = getByPublishingYear(year);

    var updatedPeriod = currentPeriod.updateWithRequest(request).toDao();
    periodRepository.save(updatedPeriod);
    LOGGER.info("Updated period successfully");
  }

  public Optional<NviPeriod> findByPublishingYear(String publishingYear) {
    var periodDao = periodRepository.findByPublishingYear(publishingYear);
    if (periodDao.isPresent()) {
      return Optional.of(NviPeriod.fromDao(periodDao.get()));
    }
    return Optional.empty();
  }

  public NviPeriod getByPublishingYear(String publishingYear) {
    return findByPublishingYear(publishingYear)
        .orElseThrow(PeriodNotFoundException.forYear(publishingYear));
  }

  public List<NviPeriod> getAll() {
    return periodRepository.getPeriods().stream().map(NviPeriod::fromDao).toList();
  }

  private NviPeriod createNewPeriodFromRequest(CreatePeriodRequest request) {
    return NviPeriod.builder()
        .withId(createPeriodId(request.publishingYear()))
        .withPublishingYear(request.publishingYear())
        .withStartDate(request.startDate())
        .withReportingDate(request.reportingDate())
        .withCreatedBy(request.createdBy())
        .withModifiedBy(request.createdBy())
        .build();
  }

  private URI createPeriodId(Integer publishingYear) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST))
        .addChild(SCIENTIFIC_INDEX_API_PATH)
        .addChild(PERIOD_PATH)
        .addChild(String.valueOf(publishingYear))
        .getUri();
  }
}
