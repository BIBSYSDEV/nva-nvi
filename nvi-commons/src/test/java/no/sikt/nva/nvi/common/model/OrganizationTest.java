package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationWithPartOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import no.sikt.nva.nvi.common.client.model.Organization;
import org.junit.jupiter.api.Test;

class OrganizationTest {

  @Test
  void shouldReturnDeepestPartOfAsTopLevelOrg() {
    var topLevelOrg = randomOrganization().build();
    var organization = randomOrganizationWithPartOf(topLevelOrg);
    var actualTopLevelOrg = organization.getTopLevelOrg();
    assertEquals(topLevelOrg, actualTopLevelOrg);
  }

  @Test
  void shouldReturnSelfAsTopLevelOrgWhenNoPartOf() {
    var organization = randomOrganization().build();
    var actualTopLevelOrg = organization.getTopLevelOrg();
    assertEquals(organization, actualTopLevelOrg);
  }

  @Test
  void shouldSerializeAndDeserializeWithoutLossOfData() throws Exception {
    var organization = randomOrganizationWithPartOf(randomOrganization().build());
    var json = organization.toJsonString();
    var actualOrganization = Organization.from(json);
    assertEquals(organization, actualOrganization);
  }

  @Test
  void shouldHandleJson() throws Exception {
    var json =
        """
{
  "@context" : "https://bibsysdev.github.io/src/organization-context.json",
  "type" : "Organization",
  "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
  "labels" : {
    "en" : "Sikt - Norwegian Agency for Shared Services in Education and Research",
    "nb" : "Sikt – Kunnskapssektorens tjenesteleverandør"
  },
  "acronym" : "SIKT",
  "country" : "NO",
  "partOf" : [ ],
  "hasPart" : [ {
    "type" : "Organization",
    "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.1.0.0",
    "labels" : {
      "en" : "The Education and Administration Division",
      "nb" : "Divisjon utdanning og administrasjon UA",
      "nn" : "Divisjon for utdanning og administrasjon"
    },
    "acronym" : "UA",
    "partOf" : [ {
      "type" : "Organization",
      "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
      "labels" : { },
      "partOf" : [ ],
      "hasPart" : [ ]
    } ],
    "hasPart" : [ ]
  }, {
    "type" : "Organization",
    "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.2.0.0",
    "labels" : {
      "en" : "The Research and Education Resources Division",
      "nb" : "Divisjon forsknings- og kunnskapsressurser",
      "nn" : "Divisjon for forskings- og kunnskapsressursar"
    },
    "acronym" : "FK",
    "partOf" : [ {
      "type" : "Organization",
      "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
      "labels" : { },
      "partOf" : [ ],
      "hasPart" : [ ]
    } ],
    "hasPart" : [ ]
  }, {
    "type" : "Organization",
    "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.3.0.0",
    "labels" : {
      "en" : "The Organisational Development Department",
      "nb" : "Divisjon data og infrastruktur",
      "nn" : "Divisjon for data og infrastruktur"
    },
    "acronym" : "DI",
    "partOf" : [ {
      "type" : "Organization",
      "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
      "labels" : { },
      "partOf" : [ ],
      "hasPart" : [ ]
    } ],
    "hasPart" : [ ]
  }, {
    "type" : "Organization",
    "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.4.0.0",
    "labels" : {
      "en" : "The division of joint services",
      "nb" : "Divisjon for fellesfunksjoner",
      "nn" : "Avdeling for organisasjonsutvikling"
    },
    "acronym" : "ORG",
    "partOf" : [ {
      "type" : "Organization",
      "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
      "labels" : { },
      "partOf" : [ ],
      "hasPart" : [ ]
    } ],
    "hasPart" : [ ]
  }, {
    "type" : "Organization",
    "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.5.0.0",
    "labels" : {
      "en" : "The Corporate Governance Department",
      "nb" : "Avdeling for virksomhetsstyring",
      "nn" : "Avdeling for verksemdstyring"
    },
    "acronym" : "VIRK",
    "partOf" : [ {
      "type" : "Organization",
      "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
      "labels" : { },
      "partOf" : [ ],
      "hasPart" : [ ]
    } ],
    "hasPart" : [ ]
  }, {
    "type" : "Organization",
    "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.6.0.0",
    "labels" : {
      "en" : "The Customer and Communication Department",
      "nb" : "Avdeling for kunde og kommunikasjon",
      "nn" : "Avdeling for kunde og kommunikasjon"
    },
    "acronym" : "KUNDE",
    "partOf" : [ {
      "type" : "Organization",
      "id" : "https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0",
      "labels" : { },
      "partOf" : [ ],
      "hasPart" : [ ]
    } ],
    "hasPart" : [ ]
  } ]
}
""";
    var actualOrganization = Organization.from(json);
    assertEquals(4, actualOrganization);
  }
}
