package no.sikt.nva.nvi.common.utils;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class CollectionUtils {

  private CollectionUtils() {}

  public static <T> List<T> copyOfNullable(Collection<T> collection) {
    return isNull(collection) ? emptyList() : collection.stream().filter(Objects::nonNull).toList();
  }

  public static <T> Set<T> copyOfNullable(Set<T> set) {
    return isNull(set)
        ? emptySet()
        : set.stream().filter(Objects::nonNull).collect(Collectors.toSet());
  }

  public static <K, V> Map<K, V> copyOfNullable(Map<K, V> map) {
    return isNull(map) ? emptyMap() : Map.copyOf(map);
  }

  public static <T> Stream<List<T>> splitIntoBatches(Collection<T> messages, int batchSize) {
    var orderedMessages = List.copyOf(messages);
    var totalSize = messages.size();
    var batchCount = (messages.size() + batchSize - 1) / batchSize;

    return IntStream.range(0, batchCount)
        .mapToObj(
            i -> {
              var from = i * batchSize;
              var to = Math.min(from + batchSize, totalSize);
              return orderedMessages.subList(from, to);
            });
  }
}
