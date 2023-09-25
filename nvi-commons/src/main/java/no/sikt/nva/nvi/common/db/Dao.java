package no.sikt.nva.nvi.common.db;

import java.util.UUID;

public interface Dao {
    String VERSION_FIELD = "version";
    UUID version();
}
