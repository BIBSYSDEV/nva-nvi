package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.dto.CreatorDb;

public record Creator(URI creatorId, List<URI> affiliations) {
    public CreatorDb toDb() {
        return new CreatorDb.Builder()
                   .withCreatorId(creatorId)
                   .withAffiliations(affiliations)
                   .build();
    }

    public static Creator fromDb(CreatorDb db) {
        return new Creator(db.getCreatorId(), db.getAffiliations());
    }

}
