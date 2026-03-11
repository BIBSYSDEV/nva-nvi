package no.sikt.nva.nvi.index.model.report;

import java.util.List;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.Pages;
import no.sikt.nva.nvi.index.model.document.PublicationChannel;

public record ReportPublicationDetails(
    String id,
    String type,
    String title,
    PublicationDateDto publicationDate,
    List<NviContributor> nviContributors,
    PublicationChannel publicationChannel,
    Pages pages,
    String language) {}
