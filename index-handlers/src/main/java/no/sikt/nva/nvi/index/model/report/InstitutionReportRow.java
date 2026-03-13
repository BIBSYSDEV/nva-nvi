package no.sikt.nva.nvi.index.model.report;

import no.sikt.nva.nvi.index.report.Column;
import no.sikt.nva.nvi.index.report.ReportRow;

public record InstitutionReportRow(
    @Column(header = "ARSTALL") String reportingYear,
    @Column(header = "NVAID") String publicationIdentifier,
    @Column(header = "ARSTALL_REG") String publishedYear,
    @Column(header = "STATUS_KONTROLLERT") String institutionApprovalStatus,
    @Column(header = "PUBLIKASJONSFORM") String publicationInstance,
    @Column(header = "PUBLISERINGSKANAL") String publicationChannel,
    @Column(header = "PUBLISERINGSKANALTYPE") String publicationChannelType,
    @Column(header = "ISSN") String publicationChannelPissn,
    @Column(header = "KVALITETSNIVAKODE") String publicationChannelLevel,
    @Column(header = "PERSONLOPENR") String contributorIdentifier,
    @Column(header = "INSTITUSJONSNR") String institutionId,
    @Column(header = "AVDNR") String facultyId,
    @Column(header = "UNDAVDNR") String departmentId,
    @Column(header = "GRUPPENR") String groupId,
    @Column(header = "ETTERNAVN") String contributorLastName,
    @Column(header = "FORNAVN") String contributorFirstName,
    @Column(header = "PUBLISERINGSKANALNAVN") String publicationChannelName,
    @Column(header = "SIDE_FRA") String pageBegin,
    @Column(header = "SIDE_TIL") String pageEnd,
    @Column(header = "SIDEANTALL") String pageCount,
    @Column(header = "VA_TITTEL") String publicationTitle,
    @Column(header = "SPRÅK") String publicationLanguage,
    @Column(header = "RAPPORTSTATUS") String globalStatus,
    @Column(header = "VEKTINGSTALL") String publicationChannelLevelPoints,
    @Column(header = "FAKTORTALL_SAMARBEID") String internationalCollaborationFactor,
    @Column(header = "FORFATTERDEL") String creatorShareCount,
    @Column(header = "FORFATTERVEKT") String pointsForAffiliation)
    implements ReportRow {}
