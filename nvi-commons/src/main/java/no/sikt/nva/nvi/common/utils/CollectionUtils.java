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
}
