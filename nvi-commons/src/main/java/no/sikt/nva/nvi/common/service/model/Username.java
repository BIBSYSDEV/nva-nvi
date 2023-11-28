package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;

public record Username(String value) {

    public static Username fromUserName(no.sikt.nva.nvi.common.db.model.Username username) {
        if (isNull(username) || isNull(username.value())) {
            return null;
        }
        return new Username(username.value());
    }
}
