package com.easternedgerobotics.rov.config;

import org.cfg4j.provider.ConfigurationProvider;
import org.cfg4j.provider.ConfigurationProviderBuilder;
import org.cfg4j.source.ConfigurationSource;
import org.cfg4j.source.compose.MergeConfigurationSource;
import org.cfg4j.source.context.environment.Environment;
import org.cfg4j.source.context.environment.ImmutableEnvironment;
import org.cfg4j.source.files.FilesConfigurationSource;

import java.io.File;
import java.nio.file.Paths;

public final class Config {
    private final ConfigurationProvider configProvider;

    public Config() {
        final Environment environment = new ImmutableEnvironment("./");
        final ConfigurationSource defaultConfigSource = new FilesConfigurationSource(() ->
            Paths.get("defaultConfig.yml")
        );
        final ConfigurationProvider defaultConfigProvider = new ConfigurationProviderBuilder()
            .withEnvironment(environment)
            .withConfigurationSource(defaultConfigSource)
            .build();

        if (new File(defaultConfigProvider.getProperty("configFile", String.class)).exists()) {
            final ConfigurationSource configSource = new MergeConfigurationSource(
                defaultConfigSource,
                new FilesConfigurationSource(() ->
                    Paths.get(defaultConfigProvider.getProperty("configFile", String.class)
                )
            ));
            configProvider = new ConfigurationProviderBuilder()
                .withEnvironment(environment)
                .withConfigurationSource(configSource)
                .build();
        } else {
            configProvider = defaultConfigProvider;
        }
    }

    public <T> T getConfig(final String name, final Class<T> bindingClass) {
        return configProvider.bind(name, bindingClass);
    }
}
