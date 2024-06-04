package no.sikt.nva.nvi.common.utils;

import java.lang.reflect.Field;
import java.time.Instant;

public final class Validator {

    private Validator() {
    }

    public static void doesNotHaveNullValues(Object object) {
        var currentClass = object.getClass();
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    if (field.get(object) == null) {
                        throw new IllegalArgumentException("Field " + field.getName() + " can not be null!");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error accessing field " + field.getName(), e);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    public static void hasInvalidLength(Integer year, int length) {
        if (year.toString().length() != length) {
            throw new IllegalArgumentException("Provided period has invalid length! Expected length: " + length);
        }
    }

    public static void isBefore(Instant startDate, Instant endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date can not be after end date!");
        }
    }

    public static void isPassedDate(Instant date) {
        if (date.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Provided date is back in time!");
        }
    }
}
