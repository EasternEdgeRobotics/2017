package com.easternedgerobotics.rov.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

public final class ValueStore<V>  {
    private static final String NAME_FORMAT = "value-%s-%s";

    private final Class<V> clazz;

    private final Preferences preferences;

    private final Map<Object, V> values = new HashMap<>();

    private final ObjectMapper mapper;

    public static <V> ValueStore<V> of(final Class<V> clazz, final String preferencesHome) {
        final ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ValueStore<>(clazz, Preferences.userRoot().node(preferencesHome), mapper);
    }

    private ValueStore(final Class<V> clazz, final Preferences preferences, final ObjectMapper mapper) {
        this.clazz = clazz;
        this.preferences = preferences;
        this.mapper = mapper;
    }

    public Optional<V> get(final Object key) {
        return Optional.ofNullable(values.computeIfAbsent(key, k -> {
            final String valueName = String.format(NAME_FORMAT, clazz.getName(), key);
            final String valueStr = preferences.get(valueName, "DEFAULT");
            if (valueStr.equals("DEFAULT")) {
                Logger.warn("The value {} does not exist.", valueName);
                return null;
            }
            try {
                return mapper.readValue(valueStr, clazz);
            } catch (final IOException e) {
                Logger.warn("The value {} could not be parsed from '{}': {}", valueName, valueStr, e);
                return null;
            }
        }));
    }

    public void set(final Object key, final V value) {
        values.put(key, value);
        final String valueName = String.format(NAME_FORMAT, clazz.getName(), key);
        try {
            preferences.put(valueName, mapper.writeValueAsString(value));
        } catch (final IOException e) {
            Logger.warn("The value {} could not be saved to {}: {}", value, valueName, e);
        }
    }
}
