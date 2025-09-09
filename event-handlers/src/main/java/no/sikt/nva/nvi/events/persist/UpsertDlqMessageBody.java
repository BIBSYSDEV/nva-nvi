package no.sikt.nva.nvi.events.persist;

import static nva.commons.core.attempt.Try.attempt;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.commons.json.JsonUtils;

public record UpsertDlqMessageBody(CandidateEvaluatedMessage evaluatedMessage, String exception)
    implements JsonSerializable {

  public static UpsertDlqMessageBody create(
      CandidateEvaluatedMessage evaluatedCandidate, Exception exception) {
    return new UpsertDlqMessageBody(evaluatedCandidate, getStackTrace(exception));
  }

  public static Optional<UpsertDlqMessageBody> fromString(String value) {
    return attempt(() -> JsonUtils.dtoObjectMapper.readValue(value, UpsertDlqMessageBody.class))
        .toOptional();
  }

  private static String getStackTrace(Exception e) {
    var stringWriter = new StringWriter();
    e.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }
}
