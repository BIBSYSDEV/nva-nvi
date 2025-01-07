package no.sikt.nva.nvi.common.service.dto;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;

public sealed interface NviCreatorDto permits VerifiedNviCreatorDto, UnverifiedNviCreatorDto {

    List<URI> affiliations();
    DbCreatorType toDao();
}
