package no.sikt.nva.nvi.common.model.events;

import java.net.URI;
import java.util.List;

public record Publication(URI id,
                          EntityDescription entityDescription) {

    public record EntityDescription(List<Contributor> contributors,
                                    Reference reference,
                                    PublicationDate publicationDate) {

        public record Reference(PublicationContext publicationContext,
                                PublicationInstance publicationInstance) {

            public record PublicationContext(String level) {

            }

            public record PublicationInstance(String type) {

            }
        }

        public record Contributor(Identity identity,

                                  List<Affiliation> affiliations) {

            public record Identity(URI id,
                                   String verificationStatus) {

            }

            public record Affiliation(URI id) {

            }
        }

        public record PublicationDate(String day,
                                      String month,
                                      String year) {

        }
    }
}
