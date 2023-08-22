package no.sikt.nva.nvi.common.model.business;

import no.sikt.nva.nvi.common.db.dto.PublicationDateDb;

public record PublicationDate(String year, String month, String day) {

    public PublicationDateDb toDb() {
        return new PublicationDateDb.Builder()
                   .withYear(year)
                   .withMonth(month)
                   .withDay(day)
                   .build();
    }

    public static PublicationDate fromDb(PublicationDateDb db) {
        return new PublicationDate(db.getYear(), db.getMonth(), db.getDay());
    }

}
