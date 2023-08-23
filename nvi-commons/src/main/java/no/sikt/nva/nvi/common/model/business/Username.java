package no.sikt.nva.nvi.common.model.business;

import no.sikt.nva.nvi.common.db.dto.UsernameDb;

public record Username(String value) {
    public UsernameDb toDb() {
        return new UsernameDb.Builder()
                   .withValue(value)
                   .build();
    }

    public static Username fromDb(UsernameDb db) {
        return new Username(db.getValue());
    }

}
