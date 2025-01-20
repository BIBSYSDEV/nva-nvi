package no.sikt.nva.nvi.common;

public interface StorageReader<T> {

  String read(T blob);
}
