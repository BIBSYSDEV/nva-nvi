package no.sikt.nva.nvi.events.evaluator;

import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.events.evaluator.calculator.CreatorVerificationUtil;

public class CreatorVerificationService {
  private final PublicationDto publication;
  private final CreatorVerificationUtil creatorVerificationUtil;

  CreatorVerificationService(
      PublicationDto publication, CreatorVerificationUtil creatorVerificationUtil) {
    this.publication = publication;
    this.creatorVerificationUtil = creatorVerificationUtil;
  }
}
