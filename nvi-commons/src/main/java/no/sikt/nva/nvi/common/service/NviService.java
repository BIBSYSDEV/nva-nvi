package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.ListingResult;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviService {

    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    public static final String NOT_SUPPORTED_REPORTING_DATE_MESSAGE = "Provided reporting date is not supported";
    public static final String START_DATE_ERROR_MESSAGE = "Period start date can not be after reporting date!";
    public static final String START_DATE_BACK_IN_TIME_ERROR_MESSAGE = "Period start date can not be back in time!";
    public static final String PERIOD_IS_MISSING_VALUES_ERROR = "Period is missing mandatory values!";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    public NviService(DynamoDbClient dynamoDbClient) {
        this.candidateRepository = new CandidateRepository(dynamoDbClient);
        this.periodRepository = new PeriodRepository(dynamoDbClient);
    }

    public NviService(PeriodRepository periodRepository, CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
    }

    @JacocoGenerated
    public static NviService defaultNviService() {
        return new NviService(defaultDynamoClient());
    }

    public DbNviPeriod createPeriod(DbNviPeriod period) {
        validateNewPeriod(period);
        return periodRepository.save(period);
    }

    public DbNviPeriod updatePeriod(DbNviPeriod period) {
        var nviPeriod = injectCreatedBy(period);
        validateUpdatePeriod(nviPeriod);
        return periodRepository.save(nviPeriod);
    }

    public DbNviPeriod getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return periodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public List<DbNviPeriod> getPeriods() {
        return periodRepository.getPeriods();
    }

    public ListingResult refresh(int pageSize, Map<String, String> startMarker) {
        return candidateRepository.refresh(pageSize, startMarker);
    }

    private static boolean isInteger(String value) {
        return attempt(() -> Integer.parseInt(value)).map(ignore -> true).orElse((ignore) -> false);
    }

    private static boolean hasInvalidLength(DbNviPeriod period) {
        return period.publishingYear().length() != 4;
    }

    private static boolean hasNullValues(DbNviPeriod period) {
        return Stream.of(period.startDate(), period.reportingDate(), period.publishingYear()).anyMatch(Objects::isNull);
    }

    private DbNviPeriod injectCreatedBy(DbNviPeriod period) {
        return period.copy().createdBy(getPeriod(period.publishingYear()).createdBy()).build();
    }

    private void validateNewPeriod(DbNviPeriod period) {
        if (hasNullValues(period)) {
            throw new IllegalArgumentException(PERIOD_IS_MISSING_VALUES_ERROR);
        }
        if (hasInvalidLength(period)) {
            throw new IllegalArgumentException(INVALID_LENGTH_MESSAGE);
        }
        if (!isInteger(period.publishingYear())) {
            throw new IllegalArgumentException(PERIOD_NOT_NUMERIC_MESSAGE);
        }
        if (period.reportingDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException(NOT_SUPPORTED_REPORTING_DATE_MESSAGE);
        }
        if (period.startDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException(START_DATE_BACK_IN_TIME_ERROR_MESSAGE);
        }
        if (period.startDate().isAfter(period.reportingDate())) {
            throw new IllegalArgumentException(START_DATE_ERROR_MESSAGE);
        }
    }

    private void validateUpdatePeriod(DbNviPeriod period) {
        if (period.reportingDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException(NOT_SUPPORTED_REPORTING_DATE_MESSAGE);
        }
        if (period.startDate().isAfter(period.reportingDate())) {
            throw new IllegalArgumentException(START_DATE_ERROR_MESSAGE);
        }
    }
}
