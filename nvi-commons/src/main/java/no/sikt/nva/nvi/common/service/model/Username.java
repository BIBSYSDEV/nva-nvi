package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;
import static nva.commons.core.StringUtils.isBlank;

import no.sikt.nva.nvi.common.exceptions.ValidationException;

public record Username(String value) {

  public static Username fromUserName(no.sikt.nva.nvi.common.db.model.Username username) {
    if (isNull(username) || isNull(username.value())) {
      return null;
    }
    return new Username(username.value());
  }

  public static Username fromString(String userName) {
    if (isBlank(userName)) {
      throw new ValidationException("Username cannot be null or empty");
    }
    return new Username(userName);
  }

  @Override
  public String toString() {
    return value;
  }
}
