package no.sikt.nva.nvi.common.validator;

import java.net.URI;
import java.util.List;

public class FakeViewingScopeValidator implements ViewingScopeValidator {

  private final boolean returnValue;

  public FakeViewingScopeValidator(boolean returnValue) {
    this.returnValue = returnValue;
  }

  @Override
  public boolean userIsAllowedToAccessAll(String userName, List<URI> organizations) {
    return returnValue;
  }

  @Override
  public boolean userIsAllowedToAccessOneOf(String userName, List<URI> organizations) {
    return returnValue;
  }
}
