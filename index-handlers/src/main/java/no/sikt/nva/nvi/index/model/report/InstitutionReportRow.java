package no.sikt.nva.nvi.index.model.report;

import no.sikt.nva.nvi.index.report.Column;
import no.sikt.nva.nvi.index.report.ReportRow;

public record InstitutionReportRow(
    @Column(header = "ARSTALL") String reportingYear,
    @Column(header = "NVAID") String publicationId,
    @Column(header = "PUBLIKASJONSFORM") String publicationInstance,
    @Column(header = "PUBLISERINGSKANAL") String publicationChannel,
    @Column(header = "PUBLISERINGSKANALTYPE") String publicationChannelType,
    @Column(header = "PRINT_ISSN") String publicationChannelPissn,
    @Column(header = "PUBLISERINGSKANALNAVN") String publicationChannelName,
    @Column(header = "KVALITETSNIVAKODE") String publicationChannelLevel,
    @Column(header = "PERSONLOPENR") String contributorIdentifier,
    @Column(header = "INSTITUSJON") String institution,
    @Column(header = "INSTITUSJON_ID") String institutionId,
    @Column(header = "INSTITUSJONSNR") String institutionIdentifier,
    @Column(header = "AVDNR") String facultyId,
    @Column(header = "UNDAVDNR") String departmentId,
    @Column(header = "GRUPPENR") String groupId,
    @Column(header = "ETTERNAVN") String contributorLastName,
    @Column(header = "FORNAVN") String contributorFirstName,
    @Column(header = "TITTEL") String publicationTitle,
    @Column(header = "STATUS_KONTROLLERT") String institutionApprovalStatus,
    @Column(header = "RAPPORTSTATUS") String globalStatus,
    @Column(header = "VEKTINGSTALL", numeric = true) String publicationChannelLevelPoints,
    @Column(header = "FAKTORTALL_SAMARBEID", numeric = true)
        String internationalCollaborationFactor,
    @Column(header = "FORFATTERDEL", numeric = true) String creatorShareCount,
    @Column(header = "TENTATIVE_PUBLISERINGSPOENG", numeric = true) String pointsForAffiliation,
    @Column(header = "PUBLISERINGSPOENG", numeric = true) String publishingPoints)
    implements ReportRow {}
