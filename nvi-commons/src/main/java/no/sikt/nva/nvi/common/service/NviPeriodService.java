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

public class NviPeriodService {

  private static final String SCIENTIFIC_INDEX_API_PATH = "scientific-index";
  private static final String PERIOD_PATH = "period";
  private static final String API_HOST = "API_HOST";
  private final Environment environment;
  private final PeriodRepository periodRepository;

  public NviPeriodService(Environment environment, PeriodRepository periodRepository) {
    this.environment = environment;
    this.periodRepository = periodRepository;
  }

  public NviPeriod create(CreatePeriodRequest request) {
    request.validate();
    var period = findByPublishingYear(request.publishingYear());
    if (period.isPresent()) {
      throw new PeriodAlreadyExistsException(
          String.format(
              "Period with publishing year %s already exists!", request.publishingYear()));
    }

    var updatedPeriod = newPeriodFromRequest(request).toDao();
    periodRepository.save(updatedPeriod);
    return fetchByPublishingYear(request.publishingYear());
  }

  public NviPeriod update(UpdatePeriodRequest request) {
    request.validate();
    var currentPeriod = fetchByPublishingYear(request.publishingYear());

    var updatedPeriod = currentPeriod.updateWithRequest(request).toDao();
    periodRepository.save(updatedPeriod);
    return fetchByPublishingYear(request.publishingYear());
  }

  public Optional<NviPeriod> findByPublishingYear(String publishingYear) {
    var periodDao = periodRepository.findByPublishingYear(publishingYear);
    if (periodDao.isPresent()) {
      return Optional.of(NviPeriod.fromDao(periodDao.get()));
    }
    return Optional.empty();
  }

  public Optional<NviPeriod> findByPublishingYear(Integer publishingYear) {
    return findByPublishingYear(String.valueOf(publishingYear));
  }

  public NviPeriod getByPublishingYear(String publishingYear) {
    return findByPublishingYear(publishingYear)
        .orElseThrow(
            () ->
                PeriodNotFoundException.withMessage(
                    String.format("Period for year %s does not exist!", publishingYear)));
  }

  public NviPeriod fetchByPublishingYear(String publishingYear) {
    return periodRepository
        .findByPublishingYear(publishingYear)
        .map(NviPeriod::fromDao)
        .orElseThrow(
            () ->
                PeriodNotFoundException.withMessage(
                    String.format("Period for year %s does not exist!", publishingYear)));
  }
     // TODO: Fix naming (get or find, not fetch)
  public NviPeriod fetchByPublishingYear(int publishingYear) {
    return fetchByPublishingYear(String.valueOf(publishingYear));
  }

  public List<NviPeriod> fetchAll() {
    return periodRepository.getPeriods().stream().map(NviPeriod::fromDao).toList();
  }

  public Optional<Integer> fetchLatestClosedPeriodYear() {
    return periodRepository.getPeriods().stream()
        .map(NviPeriod::fromDao)
        .filter(NviPeriod::isClosed)
        .map(NviPeriod::publishingYear)
        .reduce(Integer::max);
  }

  private NviPeriod newPeriodFromRequest(CreatePeriodRequest request) {
    return NviPeriod.builder()
        .withId(constructId(request.publishingYear()))
        .withPublishingYear(request.publishingYear())
        .withStartDate(request.startDate())
        .withReportingDate(request.reportingDate())
        .withCreatedBy(request.createdBy())
        .withModifiedBy(request.createdBy())
        .build();
  }

  private URI constructId(Integer publishingYear) {
    return UriWrapper.fromHost(environment.readEnv(API_HOST))
        .addChild(SCIENTIFIC_INDEX_API_PATH)
        .addChild(PERIOD_PATH)
        .addChild(String.valueOf(publishingYear))
        .getUri();
  }
}
