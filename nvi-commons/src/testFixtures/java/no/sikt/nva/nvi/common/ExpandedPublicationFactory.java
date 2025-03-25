package no.sikt.nva.nvi.common;

import static org.instancio.Select.field;

import java.net.URI;
import no.sikt.nva.nvi.test.SampleExpandedAffiliation;
import org.instancio.GeneratorSpecProvider;
import org.instancio.Instancio;
import org.instancio.Model;

public class ExpandedPublicationFactory {
  private static final String BASE_URL = "https://api.local.nva.aws.unit.no/";
  // Organization ID:
  // "id" : "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0",
  private static final String ORGANIZATION_ID_PATTERN =
      String.format(BASE_URL + "cristin/organization/#d#d#d#d.#d.#d.#d");

  // Person ID:
  // "id" : "https://api.dev.nva.aws.unit.no/cristin/person/997998",
  private static final String PERSON_ID_PATTERN =
      String.format(BASE_URL + "cristin/person/#d#d#d#d");

  // Publication ID:
  // "id" :
  // "https://api.dev.nva.aws.unit.no/publication/01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d",
  // Channel ID:
  // "id" : "https://api.dev.nva.aws.unit.no/publication-channels/journal/490845/2023",

  public static Model<SampleExpandedAffiliation> organizationModel =
      Instancio.of(SampleExpandedAffiliation.class)
          .generate(field(SampleExpandedAffiliation::id), organizationIdGenerator())
          .generate(field(SampleExpandedAffiliation::countryCode), countryCodeGenerator())
          .toModel();

  private static GeneratorSpecProvider<URI> organizationIdGenerator() {
    return gen -> gen.text().pattern(ORGANIZATION_ID_PATTERN).as(URI::create);
  }

  private static GeneratorSpecProvider<URI> personIdGenerator() {
    return gen -> gen.text().pattern(PERSON_ID_PATTERN).as(URI::create);
  }

  private static GeneratorSpecProvider<URI> publicationIdGenerator() {
    return gen -> gen.text().pattern(String.format(BASE_URL + "publication/")).as(URI::create);
  }

  private static GeneratorSpecProvider<String> countryCodeGenerator() {
    return gen -> gen.text().pattern("#C#C");
  }
  //
  //  public static Model<SampleExpandedContributor> contributorModel =
  //      Instancio.of(SampleExpandedContributor.class)
  //          .generate(
  //              field(SampleExpandedContributor::id),
  //              gen -> gen.text().wordTemplate("${adjective}-${noun}.com"))
  //          .generate(field(SampleExpandedContributor::verificationStatus), gen ->
  // gen.oneOf("Verified", "Unverified"))
  //          .generate(
  //              field(SampleExpandedContributor::contributorName),
  //              gen -> gen.text().wordTemplate("${adjective} " + "${noun}"))
  //          .generate(field(SampleExpandedContributor::role), gen -> gen.oneOf("Creator",
  // "Unknown"))
  //          .generate(
  //              field(SampleExpandedContributor::affiliations),
  //              gen -> gen.list().size(1).of(ExpandedPublicationFactory.organizationModel))
  //          .generate(field(SampleExpandedContributor::orcId), gen -> gen.text().word())
  //          .create();

  //  public static Supplier<String> websiteSupplier = () -> String.format(
  //      "https://www.%s-%s.com",
  //      Instancio
  //          .gen().text().word().adjective().get(),
  //      Instancio.gen().text().wordTemplate().noun().get());

  /*  List<Company> companies = Instancio.ofList(Company.class)
  .size(3)
  .generate(field(Company::website), gen -> gen.text().wordTemplate("${adjective}-${noun}.com"))
  .create()*/

  //  List<Company> companies = Instancio.ofList(Company.class)
  //                                     .size(3)
  //                                     .supply(field(Company::website), websiteSupplier)
  //                                     .create();
}
