package no.sikt.nva.nvi.index;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;

public class FakeCachedJwtProvider {

  private static final String TEST_TOKEN =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJPbmxpbmUgSldUIEJ1"
          + "aWxkZXIiLCJpYXQiOjE2Njg1MTE4NTcsImV4cCI6MTcwMDA0Nzg1NywiYXVkIjoi"
          + "d3d3LmV4YW1wbGUuY29tIiwic3ViIjoianJvY2tldEBleGFtcGxlLmNvbSIsIkdpd"
          + "mVuTmFtZSI6IkpvaG5ueSIsIlN1cm5hbWUiOiJSb2NrZXQiLCJFbWFpbCI6Impyb2"
          + "NrZXRAZXhhbXBsZS5jb20iLCJSb2xlIjoiTWFuYWdlciIsInNjb3BlIjoiZXhhbX"
          + "BsZS1zY29wZSJ9.ne8Jb4f2xao1zSJFZxIBRrh4WFNjkaBRV3-Ybp6fHZU";

  public static CachedJwtProvider setup() {
    var jwt = mock(DecodedJWT.class);
    var cognitoAuthenticatorMock = mock(CognitoAuthenticator.class);

    when(jwt.getToken()).thenReturn(TEST_TOKEN);
    when(jwt.getExpiresAt()).thenReturn(Date.from(Instant.now().plus(Duration.ofMinutes(5))));
    when(cognitoAuthenticatorMock.fetchBearerToken()).thenReturn(jwt);

    return new CachedJwtProvider(cognitoAuthenticatorMock, Clock.systemDefaultZone());
  }
}
