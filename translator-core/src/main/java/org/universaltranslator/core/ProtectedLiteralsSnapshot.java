package org.universaltranslator.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** 受保护字面量快照 */
public final class ProtectedLiteralsSnapshot implements Iterable<String> {
    private static final ProtectedLiteralsSnapshot EMPTY =
            new ProtectedLiteralsSnapshot(Collections.<String>emptyList());

    private final List<String> values;
    private final String cacheKey;

    private ProtectedLiteralsSnapshot(List<String> values) {
        this.values = Collections.unmodifiableList(values);
        this.cacheKey = fingerprint(values);
    }

    public static ProtectedLiteralsSnapshot empty() {
        return EMPTY;
    }

    public static ProtectedLiteralsSnapshot of(Iterable<String> source) {
        if (source == null) {
            return EMPTY;
        }
        List<String> values = new ArrayList<String>();
        for (String value : source) {
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? EMPTY : new ProtectedLiteralsSnapshot(values);
    }

    public String cacheKey() {
        return cacheKey;
    }

    @Override
    public Iterator<String> iterator() {
        return values.iterator();
    }

    private static String fingerprint(List<String> values) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        List<String> ordered = new ArrayList<String>(values);
        Collections.sort(ordered);
        for (String value : ordered) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            int length = bytes.length;
            digest.update((byte) (length >>> 24));
            digest.update((byte) (length >>> 16));
            digest.update((byte) (length >>> 8));
            digest.update((byte) length);
            digest.update(bytes);
        }
        StringBuilder output = new StringBuilder(64);
        for (byte value : digest.digest()) {
            output.append(String.format("%02x", value & 0xff));
        }
        return output.toString();
    }
}
