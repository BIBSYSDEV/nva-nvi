package no.sikt.nva.nvi.common.db;

import java.net.URI;
import java.util.UUID;
import software.amazon.awssdk.enhanced.dynamodb.Key;

public class DaoMapper {

  public static Key createCandidateKey(UUID candidateIdentifier) {
    return Key.builder()
        .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .sortValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .build();
  }

  public static Key createCandidateKeyByPublicationId(URI publicationId) {
    return Key.builder()
        .partitionValue(publicationId.toString())
        .sortValue(publicationId.toString())
        .build();
  }

  public static Key createNoteKey(UUID candidateIdentifier, UUID noteIdentifier) {
    return Key.builder()
        .partitionValue(CandidateDao.createPartitionKey(candidateIdentifier.toString()))
        .sortValue(NoteDao.createSortKey(noteIdentifier.toString()))
        .build();
  }
}
