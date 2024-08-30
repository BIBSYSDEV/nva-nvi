package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.AUTHOR_SHARE_COUNT;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.CONTRIBUTOR_IDENTIFIER;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.DEPARTMENT_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.FACULTY_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.FIRST_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.GLOBAL_STATUS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.GROUP_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.INTERNATIONAL_COLLABORATION_FACTOR;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.LAST_NAME;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.POINTS_FOR_AFFILIATION;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_CHANNEL_LEVEL_POINTS;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_IDENTIFIER;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_INSTANCE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLICATION_TITLE;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.PUBLISHED_YEAR;
import static no.sikt.nva.nvi.index.model.report.InstitutionReportHeader.REPORTING_YEAR;
import java.util.List;

public final class InstitutionReportHeaders {

    public static final List<String> INSTITUTION_REPORT_HEADERS = List.of(REPORTING_YEAR.getValue(),
                                                                          PUBLICATION_IDENTIFIER.getValue(),
                                                                          PUBLISHED_YEAR.getValue(),
                                                                          INSTITUTION_APPROVAL_STATUS.getValue(),
                                                                          PUBLICATION_INSTANCE.getValue(),
                                                                          CONTRIBUTOR_IDENTIFIER.getValue(),
                                                                          INSTITUTION_ID.getValue(),
                                                                          FACULTY_ID.getValue(),
                                                                          DEPARTMENT_ID.getValue(),
                                                                          GROUP_ID.getValue(),
                                                                          LAST_NAME.getValue(),
                                                                          FIRST_NAME.getValue(),
                                                                          PUBLICATION_TITLE.getValue(),
                                                                          GLOBAL_STATUS.getValue(),
                                                                          PUBLICATION_CHANNEL_LEVEL_POINTS.getValue(),
                                                                          INTERNATIONAL_COLLABORATION_FACTOR.getValue(),
                                                                          AUTHOR_SHARE_COUNT.getValue(),
                                                                          POINTS_FOR_AFFILIATION.getValue());

    private InstitutionReportHeaders() {
    }
}
