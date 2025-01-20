package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
public class UsernamePasswordWrapper {

  @JsonProperty("username")
  public String username;

  @JsonProperty("password")
  public String password;

  public UsernamePasswordWrapper(
      @JsonProperty("username") String username, @JsonProperty("password") String password) {
    this.username = username;
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
