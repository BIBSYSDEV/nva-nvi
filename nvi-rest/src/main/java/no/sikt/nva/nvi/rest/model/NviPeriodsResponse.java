package no.sikt.nva.nvi.rest.model;

import java.util.List;
import no.sikt.nva.nvi.common.service.dto.NviPeriodDto;

public record NviPeriodsResponse(List<NviPeriodDto> periods) {

}
