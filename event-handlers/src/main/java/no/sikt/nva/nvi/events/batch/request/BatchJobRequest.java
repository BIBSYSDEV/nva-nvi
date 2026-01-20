package no.sikt.nva.nvi.events.batch.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartBatchJobRequest.class, name = "StartBatchJobRequest"),
  @JsonSubTypes.Type(value = CandidatesByYearRequest.class, name = "CandidatesByYearRequest"),
  @JsonSubTypes.Type(value = CandidateScanRequest.class, name = "CandidateScanBatchJobRequest")
})
public sealed interface BatchJobRequest extends JsonSerializable
    permits StartBatchJobRequest, CandidatesByYearRequest, CandidateScanRequest {

  BatchJobType jobType();
}
