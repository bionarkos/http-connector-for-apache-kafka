/*
 * Copyright 2019 Aiven Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.aiven.kafka.connect.http.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.config.types.Password;
import org.apache.kafka.connect.errors.ConnectException;

public class HttpSinkConfig extends AbstractConfig {
    private static final String CONNECTION_GROUP = "Connection";
    private static final String HTTP_URL_CONFIG = "http.url";
    private static final String HTTP_AUTHORIZATION_TYPE_CONFIG = "http.authorization.type";
    private static final String HTTP_HEADERS_AUTHORIZATION_CONFIG = "http.headers.authorization";
    private static final String HTTP_HEADERS_CONTENT_TYPE_CONFIG = "http.headers.content.type";

    private static final String BATCHING_GROUP = "Batching";
    private static final String BATCHING_ENABLED_CONFIG = "batching.enabled";
    private static final String BATCH_MAX_SIZE_CONFIG = "batch.max.size";

    private static final String DELIVERY_GROUP = "Delivery";
    private static final String MAX_RETRIES_CONFIG = "max.retries";
    private static final String RETRY_BACKOFF_MS_CONFIG = "retry.backoff.ms";

    public static final String NAME_CONFIG = "name";

    public static ConfigDef configDef() {
        final ConfigDef configDef = new ConfigDef();
        addConnectionConfigGroup(configDef);
        addBatchingConfigGroup(configDef);
        addRetriesConfigGroup(configDef);
        return configDef;
    }

    private static void addConnectionConfigGroup(final ConfigDef configDef) {
        int groupCounter = 0;
        configDef.define(
            HTTP_URL_CONFIG,
            ConfigDef.Type.STRING,
            ConfigDef.NO_DEFAULT_VALUE,
            new ConfigDef.Validator() {
                @Override
                public void ensureValid(final String name, final Object value) {
                    if (value == null) {
                        throw new ConfigException(HTTP_URL_CONFIG, value, "can't be null");
                    }
                    if (!(value instanceof String)) {
                        throw new ConfigException(HTTP_URL_CONFIG, value, "must be string");
                    }
                    try {
                        new URL((String) value);
                    } catch (final MalformedURLException e) {
                        throw new ConfigException(HTTP_URL_CONFIG, value, "malformed URL");
                    }
                }

                @Override
                public String toString() {
                    return "HTTP(S) ULRs";
                }
            },
            ConfigDef.Importance.HIGH,
            "The URL to send data to.",
            CONNECTION_GROUP,
            groupCounter++,
            ConfigDef.Width.LONG,
            HTTP_URL_CONFIG
        );

        configDef.define(
            HTTP_AUTHORIZATION_TYPE_CONFIG,
            ConfigDef.Type.STRING,
            ConfigDef.NO_DEFAULT_VALUE,
            new ConfigDef.Validator() {
                @Override
                public void ensureValid(final String name, final Object value) {
                    if (value == null) {
                        throw new ConfigException(HTTP_AUTHORIZATION_TYPE_CONFIG, value);
                    }
                    assert value instanceof String;
                    final String valueStr = (String) value;
                    if (!AuthorizationType.names().contains(valueStr)) {
                        throw new ConfigException(
                            HTTP_AUTHORIZATION_TYPE_CONFIG, valueStr,
                            "supported values are: " + AuthorizationType.names());
                    }
                }

                @Override
                public String toString() {
                    return AuthorizationType.names().toString();
                }
            },
            ConfigDef.Importance.HIGH,
            "The HTTP authorization type.",
            CONNECTION_GROUP,
            groupCounter++,
            ConfigDef.Width.SHORT,
            HTTP_AUTHORIZATION_TYPE_CONFIG,
            List.of(HTTP_HEADERS_AUTHORIZATION_CONFIG),
            FixedSetRecommender.ofSupportedValues(AuthorizationType.names())
        );

        configDef.define(
            HTTP_HEADERS_AUTHORIZATION_CONFIG,
            ConfigDef.Type.PASSWORD,
            null,
            ConfigDef.Importance.MEDIUM,
            "The static content of Authorization header. "
                + "Must be set along with 'static' authorization type.",
            CONNECTION_GROUP,
            groupCounter++,
            ConfigDef.Width.MEDIUM,
            HTTP_HEADERS_AUTHORIZATION_CONFIG,
            new ConfigDef.Recommender() {
                @Override
                public List<Object> validValues(final String name, final Map<String, Object> parsedConfig) {
                    return List.of();
                }

                @Override
                public boolean visible(final String name, final Map<String, Object> parsedConfig) {
                    return AuthorizationType.STATIC.name.equalsIgnoreCase(
                        (String) parsedConfig.get(HTTP_AUTHORIZATION_TYPE_CONFIG));
                }
            });

        configDef.define(
            HTTP_HEADERS_CONTENT_TYPE_CONFIG,
            ConfigDef.Type.STRING,
            null,
            ConfigDef.Importance.LOW,
            "The value of Content-Type that will be send with each request.",
            CONNECTION_GROUP,
            groupCounter++,
            ConfigDef.Width.MEDIUM,
            HTTP_HEADERS_CONTENT_TYPE_CONFIG
        );
    }

    private static void addBatchingConfigGroup(final ConfigDef configDef) {
        int groupCounter = 0;
        configDef.define(
            BATCHING_ENABLED_CONFIG,
            ConfigDef.Type.BOOLEAN,
            false,
            ConfigDef.Importance.HIGH,
            "Whether to enable batching multiple records in a single HTTP request.",
            BATCHING_GROUP,
            groupCounter++,
            ConfigDef.Width.SHORT,
            BATCHING_ENABLED_CONFIG
        );

        configDef.define(
            BATCH_MAX_SIZE_CONFIG,
            ConfigDef.Type.INT,
            500,
            ConfigDef.Range.between(1, 1_000_000),
            ConfigDef.Importance.MEDIUM,
            "The maximum size of a record batch to be sent in a single HTTP request.",
            BATCHING_GROUP,
            groupCounter++,
            ConfigDef.Width.MEDIUM,
            BATCHING_GROUP
        );
    }

    private static void addRetriesConfigGroup(final ConfigDef configDef) {
        int groupCounter = 0;
        configDef.define(
            MAX_RETRIES_CONFIG,
            ConfigDef.Type.INT,
            1,
            ConfigDef.Range.atLeast(0),
            ConfigDef.Importance.MEDIUM,
            "The maximum number of times to retry on errors when sending a batch before failing the task.",
            DELIVERY_GROUP,
            groupCounter++,
            ConfigDef.Width.SHORT,
            MAX_RETRIES_CONFIG
        );
        configDef.define(
            RETRY_BACKOFF_MS_CONFIG,
            ConfigDef.Type.INT,
            3000,
            ConfigDef.Range.atLeast(0),
            ConfigDef.Importance.MEDIUM,
            "The time in milliseconds to wait following an error before a retry attempt is made.",
            DELIVERY_GROUP,
            groupCounter++,
            ConfigDef.Width.SHORT,
            RETRY_BACKOFF_MS_CONFIG
        );
    }

    public HttpSinkConfig(final Map<String, String> properties) {
        super(configDef(), properties);
        validate();
    }

    private void validate() {
        switch (authorizationType()) {
            case STATIC:
                if (headerAuthorization() == null || headerAuthorization().isBlank()) {
                    throw new ConfigException(
                        HTTP_HEADERS_AUTHORIZATION_CONFIG,
                        getPassword(HTTP_HEADERS_AUTHORIZATION_CONFIG),
                        "Must be present when " + HTTP_HEADERS_CONTENT_TYPE_CONFIG
                            + " = " + AuthorizationType.STATIC);
                }
                break;

            case NONE:
                if (headerAuthorization() != null && !headerAuthorization().isBlank()) {
                    throw new ConfigException(
                        HTTP_HEADERS_AUTHORIZATION_CONFIG,
                        getPassword(HTTP_HEADERS_AUTHORIZATION_CONFIG),
                        "Must not be present when " + HTTP_HEADERS_CONTENT_TYPE_CONFIG
                            + " != " + AuthorizationType.STATIC);
                }
                break;

            default:
                break;
        }
    }

    public final URL httpUrl() {
        try {
            return new URL(getString(HTTP_URL_CONFIG));
        } catch (final MalformedURLException e) {
            throw new ConnectException(e);
        }
    }

    public AuthorizationType authorizationType() {
        return AuthorizationType.forName(getString(HTTP_AUTHORIZATION_TYPE_CONFIG));
    }

    public final String headerAuthorization() {
        final Password authPasswd = getPassword(HTTP_HEADERS_AUTHORIZATION_CONFIG);
        return authPasswd != null ? authPasswd.value() : null;
    }

    public final String headerContentType() {
        return getString(HTTP_HEADERS_CONTENT_TYPE_CONFIG);
    }

    public final boolean batchingEnabled() {
        return getBoolean(BATCHING_ENABLED_CONFIG);
    }

    public final int batchMaxSize() {
        return getInt(BATCH_MAX_SIZE_CONFIG);
    }

    public int maxRetries() {
        return getInt(MAX_RETRIES_CONFIG);
    }

    public int retryBackoffMs() {
        return getInt(RETRY_BACKOFF_MS_CONFIG);
    }

    public final String connectorName() {
        return originalsStrings().get(NAME_CONFIG);
    }

    public static void main(final String... args) {
        System.out.println("=========================================");
        System.out.println("HTTP Sink connector Configuration Options");
        System.out.println("=========================================");
        System.out.println();
        System.out.println(configDef().toEnrichedRst());
    }
}