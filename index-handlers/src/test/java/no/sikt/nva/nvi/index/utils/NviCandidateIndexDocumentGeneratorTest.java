package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import no.sikt.nva.nvi.common.model.SampleCandidateGenerator;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.ExpandedResourceGenerator;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NviCandidateIndexDocumentGeneratorTest {

  @ParameterizedTest
  @EnumSource(
      value = Sector.class,
      names = {"UNKNOWN"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldPopulateSectorInApprovalViewWhenSectorIsNotUnknown(Sector sector) {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, sector);

    var indexDocument = generateIndexDocument(candidate);

    var approval = getApprovalView(indexDocument, institutionId);

    assertEquals(sector.toString(), approval.sector());
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsUnknown() {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, Sector.UNKNOWN);

    var indexDocument = generateIndexDocument(candidate);

    var approval = getApprovalView(indexDocument, institutionId);

    assertNull(approval.sector());
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsNull() {
    var institutionId = randomUri();
    var candidate = candidateWithInstitutionSector(institutionId, null);

    var indexDocument = generateIndexDocument(candidate);

    var approval = getApprovalView(indexDocument, institutionId);

    assertNull(approval.sector());
  }

  private static Candidate candidateWithInstitutionSector(URI institutionId, Sector sector) {
    return new SampleCandidateGenerator()
        .withInstitutionPoints(institutionId, sector, randomBigDecimal())
        .build();
  }

  private static NviCandidateIndexDocument generateIndexDocument(Candidate candidate) {
    return new NviCandidateIndexDocumentGenerator(
            mock(UriRetriever.class), expandedResourceFromCandidate(candidate), candidate)
        .generateDocument();
  }

  private static JsonNode expandedResourceFromCandidate(Candidate candidate) {
    return ExpandedResourceGenerator.builder()
        .withCandidate(candidate)
        .build()
        .createExpandedResource();
  }

  private ApprovalView getApprovalView(NviCandidateIndexDocument document, URI institutionId) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findFirst()
        .orElse(null);
  }
}
