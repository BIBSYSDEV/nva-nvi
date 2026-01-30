package no.sikt.nva.nvi.common;

@FunctionalInterface
public interface StorageReader<T> {

  String read(T blob);
}
