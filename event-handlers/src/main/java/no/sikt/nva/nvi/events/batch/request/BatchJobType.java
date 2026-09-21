package no.sikt.nva.nvi.events.batch.request;

public enum BatchJobType {
  REFRESH_CANDIDATES,
  BACKFILL_CREATOR_DATA,
  BACKFILL_CHANNEL_METADATA,
  REFRESH_PERIODS,
  REPORT_APPROVED_CANDIDATES
}
