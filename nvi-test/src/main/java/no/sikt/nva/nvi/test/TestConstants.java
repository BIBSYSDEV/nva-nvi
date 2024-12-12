package no.sikt.nva.nvi.test;

import java.net.URI;

public final class TestConstants {

    public static final String ID_FIELD = "id";
    public static final String IDENTIFIER_FIELD = "identifier";
    public static final String IDENTITY_FIELD = "identity";
    public static final String IDENTITY = "Identity";
    public static final String TYPE_FIELD = "type";
    public static final String NAME_FIELD = "name";
    public static final String LABELS_FIELD = "labels";
    public static final String LEVEL_FIELD = "level";
    public static final String PAGES_FIELD = "pages";
    public static final String ROLE_FIELD = "role";
    public static final String STATUS_FIELD = "status";
    public static final String CONTRIBUTOR = "Contributor";
    public static final String CONTRIBUTORS_FIELD = "contributors";
    public static final String CREATOR = "Creator";
    public static final String HARDCODED_NORWEGIAN_LABEL = "Hardcoded Norwegian label";
    public static final String HARDCODED_ENGLISH_LABEL = "Hardcoded English label";
    public static final String NB_FIELD = "nb";
    public static final String EN_FIELD = "en";
    public static final String COUNTRY_CODE_FIELD = "countryCode";
    public static final String COUNTRY_CODE_NORWAY = "NO";
    public static final String COUNTRY_CODE_SWEDEN = "SE";

    public static final URI HARDCODED_CREATOR_ID = URI.create("https://api.dev.nva.aws.unit.no/cristin/person/997998");
    public static final SampleExpandedPublicationDate HARDCODED_JSON_PUBLICATION_DATE =
        new SampleExpandedPublicationDate(
        "2023",
        null,
        null);
    public static final URI HARDCODED_PUBLICATION_CHANNEL_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/publication-channels/series/490845/2023");
    public static final URI HARDCODED_PUBLICATION_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d");
    public static final URI CRISTIN_NVI_ORG_TOP_LEVEL_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/194.0.0.0");
    public static final URI CRISTIN_NVI_ORG_SUB_UNIT_ID = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/194.64.20.0");

    public static final int ONE = 1;
    public static final String AFFILIATIONS_FIELD = "affiliations";
    public static final String ORCID_FIELD = "orcid";
    public static final String VERIFICATION_STATUS_FIELD = "verificationStatus";
    public static final String MAIN_TITLE_FIELD = "mainTitle";
    public static final String PUBLICATION_DATE_FIELD = "publicationDate";
    public static final String PUBLICATION_INSTANCE_FIELD = "publicationInstance";
    public static final String PUBLICATION_CONTEXT_FIELD = "publicationContext";
    public static final String REFERENCE_FIELD = "reference";
    public static final String ENTITY_DESCRIPTION_FIELD = "entityDescription";
    public static final String TOP_LEVEL_ORGANIZATIONS_FIELD = "topLevelOrganizations";
    public static final String BODY_FIELD = "body";
    public static final String LANGUAGE_FIELD = "language";
    public static final String ABSTRACT_FIELD = "abstract";

    private TestConstants() {
    }
}
