package no.sikt.nva.nvi.common.service.model;

import java.net.URI;
import no.sikt.nva.nvi.common.db.model.ChannelType;

public record PublicationChannel(ChannelType channelType,
                                 URI id,
                                 String level) {

}
