package com.bikestore;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared Jackson mapper. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}
}
