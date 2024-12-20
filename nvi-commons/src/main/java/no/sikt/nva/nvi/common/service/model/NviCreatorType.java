package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;

public sealed interface NviCreatorType permits VerifiedNviCreator, UnverifiedNviCreator {

    List<URI> affiliations();
    DbCreatorType toDao();
}
