package io.github.nanoforged.fml.api;

import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Identifier(String namespace, String path) implements Comparable<Identifier> {

    public static final String DEFAULT_NAMESPACE = "flux";
    public static final char SEPARATOR = ':';

    private static final Pattern IDENTIFIER = Pattern.compile(
            "(?:(?<namespace>[a-z0-9_.-]+):)?(?<path>[a-z0-9_./-]+)");

    public static Optional<Identifier> tryParse(String s) {
        if (s == null) return Optional.empty();
        Matcher m = IDENTIFIER.matcher(s);
        if (!m.matches()) return Optional.empty();
        String ns = m.group("namespace");
        return Optional.of(new Identifier(ns == null ? DEFAULT_NAMESPACE : ns, m.group("path")));
    }

    public static Identifier parse(String s) {
        return tryParse(s).orElseThrow(() -> new IllegalArgumentException("invalid identifier: " + s));
    }

    public static boolean isValid(String s) {
        return tryParse(s).isPresent();
    }

    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier of(String path) {
        return new Identifier(DEFAULT_NAMESPACE, path);
    }

    @Override
    public @NonNull String toString() {
        return namespace + SEPARATOR + path;
    }

    @Override
    public int compareTo(@NonNull Identifier id) {
        int c = namespace.compareTo(id.namespace);
        return c != 0 ? c : path.compareTo(id.path);
    }
}