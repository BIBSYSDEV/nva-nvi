package no.sikt.nva.nvi.events.batch;

import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.events.batch.model.ReportingYearFilter;
import no.sikt.nva.nvi.events.batch.model.StartBatchJobRequest;
import no.sikt.nva.nvi.events.batch.model.YearQueryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YearQueryProcessor implements BatchJobProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(YearQueryProcessor.class);

    public static StartBatchJobRequest createYearQueryEvent(StartBatchJobRequest input) {
        var yearFilter = (ReportingYearFilter) input.filter();
        var yearQueryState = YearQueryState.forYears(yearFilter.reportingYears());
        return input.copy().withPaginationState(yearQueryState).build();
    }

}
