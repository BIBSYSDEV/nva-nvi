package cucumber.contexts;

import static java.util.Objects.nonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;

public class NviCreatorContext {

  private String name;
  private URI id;
  private List<URI> affiliations = new ArrayList<>();
  private NviCreatorDto dto;
  private DbCreatorType dbEntity;

  public NviCreatorContext() {}

  public NviCreatorDto getDto() {
    return dto;
  }

  public DbCreatorType getDbEntity() {
    return dbEntity;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public URI getId() {
    return id;
  }

  public void setId(URI id) {
    this.id = id;
  }

  public List<URI> getAffiliations() {
    return affiliations;
  }

  public void setAffiliations(List<URI> affiliations) {
    this.affiliations = affiliations;
  }

  public void addAffiliation(URI affiliation) {
    affiliations.add(affiliation);
  }

  public void buildContributorDto() {
    if (nonNull(this.id)) {
      dto = VerifiedNviCreatorDto.builder().withId(id).withAffiliations(affiliations).build();
    } else {
      dto = UnverifiedNviCreatorDto.builder().withName(name).withAffiliations(affiliations).build();
    }
  }

  public void buildDbEntity() {
    if (dto instanceof VerifiedNviCreatorDto) {
      dbEntity = ((VerifiedNviCreatorDto) dto).toDao();
    } else {
      dbEntity = ((UnverifiedNviCreatorDto) dto).toDao();
    }
  }
}
