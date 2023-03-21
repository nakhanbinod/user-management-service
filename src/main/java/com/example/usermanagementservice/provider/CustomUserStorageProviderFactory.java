package com.example.usermanagementservice.provider;

import com.example.usermanagementservice.util.DatabaseConnection;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;

public class CustomUserStorageProviderFactory implements UserStorageProviderFactory<CustomUserStorageProvider> {
    private static final String UPWORK_USER_PROVIDER = "upwork";

    private static final Logger logger = LoggerFactory.getLogger(CustomUserStorageProviderFactory.class);

    protected List<ProviderConfigProperty> configMetadata;

    protected Properties properties = new Properties();

    @Override
    public void init(Config.Scope config) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("/application.properties");

        if (is == null) {
            logger.warn("Could not find legacy.properties in classpath");
        } else {
            try {
                properties.load(is);

                ProviderConfigurationBuilder builder = ProviderConfigurationBuilder.create();

                builder.property("config.key.jdbc.driver", "JDBC Driver Class",
                        "JDBC Driver Class", ProviderConfigProperty.STRING_TYPE,
                        properties.get("config.key.jdbc.driver"), null);

                builder.property("config.key.jdbc.url", "JDBC Url",
                        "JDBC Url", ProviderConfigProperty.STRING_TYPE,
                        properties.get("config.key.jdbc.url"), null);

                builder.property("config.key.db.username", "DB Username",
                        "DB Username", ProviderConfigProperty.STRING_TYPE,
                        properties.get("config.key.db.username"), null);

                builder.property("config.key.db.password", "DB password",
                        "DB Password", ProviderConfigProperty.STRING_TYPE,
                        properties.get("config.key.db.password"), null);

                builder.property("config.key.validation.query", "Validation Query",
                        "Validation Query", ProviderConfigProperty.STRING_TYPE,
                        properties.get("config.key.validation.query"), null);

                configMetadata = builder.build();

            } catch (IOException ex) {
                logger.error("Failed to load legacy.properties file", ex);
            }
        }
    }


    @Override
    public CustomUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.info("creating new LegacyUserStorageProvider");
        return new CustomUserStorageProvider(session, model);
    }

    @Override
    public String getId() {
        return UPWORK_USER_PROVIDER;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configMetadata;
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config)
            throws ComponentValidationException {

        try (Connection c = DatabaseConnection.getConnection(config)) {
            logger.info("Testing connection");
            c.createStatement().execute(config.get("config.key.validation.query"));
            logger.info("Connection OK");
        } catch (Exception ex) {
            logger.warn("Unable to validate connection: ex={}", ex.getMessage());
            throw new ComponentValidationException("Unable to validate database connection", ex);
        }
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        logger.info("onUpdate()");
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        logger.info("onCreate()");
    }
}
