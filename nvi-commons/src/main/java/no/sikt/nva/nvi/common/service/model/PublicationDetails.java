package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;

@JsonSerialize
public record PublicationDetails(URI publicationId,
                                 URI publicationBucketUri,
                                 String type,
                                 PublicationDate publicationDate,
                                 List<Creator> creators,
                                 PublicationChannel publicationChannel) {

    public static PublicationDetails from(CandidateDao candidateDao) {
        var dbCandidate = candidateDao.candidate();
        return new PublicationDetails(dbCandidate.publicationId(),
                                      dbCandidate.publicationBucketUri(),
                                      dbCandidate.instanceType(),
                                      PublicationDate.from(dbCandidate.publicationDate()),
                                      dbCandidate.creators()
                                          .stream()
                                          .map(Creator::from)
                                          .toList(),
                                      new PublicationChannel(dbCandidate.channelType(),
                                                             dbCandidate.channelId(),
                                                             dbCandidate.level().toString()));
    }

    public List<URI> getNviCreatorAffiliations() {
        return creators.stream()
                   .map(Creator::affiliations)
                   .flatMap(List::stream)
                   .toList();
    }

    public record PublicationDate(String year, String month, String day) {

        public static PublicationDate from(DbPublicationDate dbPublicationDate) {
            return new PublicationDate(dbPublicationDate.year(),
                                       dbPublicationDate.month(),
                                       dbPublicationDate.day());
        }
    }

    public record Creator(URI id, List<URI> affiliations) {

        public static Creator from(DbCreator dbCreator) {
            return new Creator(dbCreator.creatorId(),
                               dbCreator.affiliations());
        }
    }
}
