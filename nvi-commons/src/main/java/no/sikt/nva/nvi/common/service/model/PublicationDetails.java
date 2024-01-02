package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.db.model.ChannelType;

@JsonSerialize
public record PublicationDetails(URI publicationId,
                                 URI publicationBucketUri,
                                 String type,
                                 PublicationDate publicationDate,
                                 List<Creator> creators,
                                 ChannelType channelType,
                                 URI publicationChannelId,
                                 String level) {

    public record PublicationDate(String year, String month, String day) {

    }

    public record Creator(URI id, List<URI> affiliations) {

    }
}
