package no.sikt.nva.nvi.common.service.model;

import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;
import no.sikt.nva.nvi.common.service.exception.PeriodAlreadyExistsException;
import no.sikt.nva.nvi.common.service.exception.PeriodNotFoundException;
import no.sikt.nva.nvi.common.service.requests.CreatePeriodRequest;
import no.sikt.nva.nvi.common.service.requests.UpdatePeriodRequest;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public class NviPeriod {

    public static final String SCIENTIFIC_INDEX_API_PATH = "scientific-index";
    public static final String PERIOD_PATH = "period";
    public static final String PUBLISHING_YEAR_EXISTS = "Period with publishing year %s already exists!";
    private static final String API_HOST = new Environment().readEnv("API_HOST");
    private final URI id;
    private final Integer publishingYear;
    private final Instant startDate;
    private final Instant reportingDate;
    private final Username createdBy;
    private final Username modifiedBy;

    protected NviPeriod(URI id, Integer publishingYear, Instant startDate, Instant reportingDate, Username createdBy,
                        Username modifiedBy) {
        this.id = id;
        this.publishingYear = publishingYear;
        this.startDate = startDate;
        this.reportingDate = reportingDate;
        this.createdBy = createdBy;
        this.modifiedBy = modifiedBy;
    }

    public static NviPeriod create(CreatePeriodRequest request, PeriodRepository periodRepository) {
        var period = NviPeriod.fromRequest(request);
        if (period.exists(periodRepository)) {
            throw new PeriodAlreadyExistsException(String.format(PUBLISHING_YEAR_EXISTS, period.getPublishingYear()));
        }
        period.persist(periodRepository);
        return period.fetch(periodRepository);
    }

    public static NviPeriod update(UpdatePeriodRequest request, PeriodRepository periodRepository) {
        var period = NviPeriod.fromRequest(request);
        period.fetch(periodRepository).updateWithRequest(request).persist(periodRepository);
        return period.fetch(periodRepository);
    }

    public static NviPeriod fetch(String publishingYear, PeriodRepository periodRepository) {
        return periodRepository.findByPublishingYear(publishingYear)
                   .map(NviPeriod::fromDbObject)
                   .orElseThrow(() -> PeriodNotFoundException.withMessage(
                       String.format("Period for year %s does not exist!", publishingYear)));
    }

    public static NviPeriod fromDbObject(DbNviPeriod dbNviPeriod) {
        return new NviPeriod(dbNviPeriod.id(), Integer.parseInt(dbNviPeriod.publishingYear()),
                             dbNviPeriod.startDate(), dbNviPeriod.reportingDate(),
                             Username.fromUserName(dbNviPeriod.createdBy()),
                             Username.fromUserName(dbNviPeriod.modifiedBy()));
    }

    public URI getId() {
        return id;
    }

    public Integer getPublishingYear() {
        return publishingYear;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getReportingDate() {
        return reportingDate;
    }

    public Username getCreatedBy() {
        return createdBy;
    }

    public Username getModifiedBy() {
        return modifiedBy;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(id, publishingYear, startDate, reportingDate);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NviPeriod nviPeriod = (NviPeriod) o;
        return Objects.equals(id, nviPeriod.id)
               && Objects.equals(publishingYear, nviPeriod.publishingYear)
               && Objects.equals(startDate, nviPeriod.startDate)
               && Objects.equals(reportingDate, nviPeriod.reportingDate);
    }

    public NviPeriodDto toDto() {
        return new NviPeriodDto(id, publishingYear.toString(), startDate.toString(), reportingDate.toString());
    }

    public boolean isClosedPeriod() {
        return reportingDate.isBefore(Instant.now());
    }

    private static Builder builder() {
        return new Builder();
    }

    private static NviPeriod fromRequest(CreatePeriodRequest request) {
        request.validate();
        return new NviPeriod(constructId(String.valueOf(request.publishingYear())), request.publishingYear(),
                             request.startDate(), request.reportingDate(), request.createdBy(), null);
    }

    private static URI constructId(String publishingYear) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(SCIENTIFIC_INDEX_API_PATH)
                   .addChild(PERIOD_PATH)
                   .addChild(publishingYear)
                   .getUri();
    }

    private static NviPeriod fromRequest(UpdatePeriodRequest request) {
        request.validate();
        return new NviPeriod(constructId(String.valueOf(request.publishingYear())), request.publishingYear(),
                             request.startDate(), request.reportingDate(), null, request.modifiedBy());
    }

    private NviPeriod fetch(PeriodRepository periodRepository) {
        return fetch(String.valueOf(publishingYear), periodRepository);
    }

    private NviPeriod updateWithRequest(UpdatePeriodRequest request) {
        return this.copy()
                   .withStartDate(request.startDate())
                   .withReportingDate(request.reportingDate())
                   .withModifiedBy(request.modifiedBy())
                   .build();
    }

    private void persist(PeriodRepository periodRepository) {
        periodRepository.save(this.toDbObject());
    }

    private boolean exists(PeriodRepository periodRepository) {
        return attempt(() -> fetch(periodRepository)).isSuccess();
    }

    private Builder copy() {
        return builder()
                   .withId(id)
                   .withPublishingYear(publishingYear)
                   .withStartDate(startDate)
                   .withReportingDate(reportingDate)
                   .withCreatedBy(createdBy)
                   .withModifiedBy(modifiedBy);
    }

    private DbNviPeriod toDbObject() {
        return DbNviPeriod.builder()
                   .id(this.id)
                   .publishingYear(String.valueOf(this.publishingYear))
                   .startDate(this.startDate)
                   .reportingDate(this.reportingDate)
                   .createdBy(no.sikt.nva.nvi.common.db.model.Username.fromUserName(this.createdBy))
                   .modifiedBy(no.sikt.nva.nvi.common.db.model.Username.fromUserName(this.modifiedBy))
                   .build();
    }

    public static final class Builder {

        private URI id;
        private Integer publishingYear;
        private Instant startDate;
        private Instant reportingDate;
        private Username createdBy;
        private Username modifiedBy;

        private Builder() {
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withPublishingYear(Integer publishingYear) {
            this.publishingYear = publishingYear;
            return this;
        }

        public Builder withStartDate(Instant startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder withReportingDate(Instant reportingDate) {
            this.reportingDate = reportingDate;
            return this;
        }

        public Builder withCreatedBy(Username createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder withModifiedBy(Username modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }

        public NviPeriod build() {
            return new NviPeriod(id, publishingYear, startDate, reportingDate, createdBy, modifiedBy);
        }
    }
}
