package no.sikt.nva.nvi.events.batch.job;

@FunctionalInterface
public interface BatchJob {

  BatchJobResult execute();
}
