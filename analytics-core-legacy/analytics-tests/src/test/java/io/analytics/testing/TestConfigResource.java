/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.analytics.testing;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Key;
import io.analytics.base.client.duckdb.DuckDBConfig;
import io.analytics.base.config.ConfigManager;
import io.analytics.base.config.AnalyticsConfig;
import io.analytics.base.dto.Manifest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.analytics.base.client.duckdb.DuckDBConfig.DUCKDB_CACHE_TASK_RETRY_DELAY;
import static io.analytics.base.client.duckdb.DuckDBConfig.DUCKDB_MAX_CACHE_QUERY_TIMEOUT;
import static io.analytics.base.client.duckdb.DuckDBConfig.DUCKDB_MAX_CONCURRENT_TASKS;
import static io.analytics.base.client.duckdb.DuckDBConfig.DUCKDB_MEMORY_LIMIT;
import static io.analytics.base.config.ConfigManager.ConfigEntry.configEntry;
import static io.analytics.base.config.AnalyticsConfig.DataSourceType.DUCKDB;
import static io.analytics.base.config.AnalyticsConfig.ANALYTICS_DATASOURCE_TYPE;
import static io.analytics.base.config.AnalyticsConfig.ANALYTICS_DIRECTORY;
import static io.analytics.testing.AbstractTestFramework.withDefaultCatalogSchema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every test will affect each other, so we need to run them in single thread.
 */
@Test(singleThreaded = true)
public class TestConfigResource
        extends RequireAnalyticsServer
{
    private Path mdlDir;

    @Override
    protected TestingAnalyticsServer createAnalyticsServer()
    {
        Manifest manifest = withDefaultCatalogSchema()
                .build();

        try {
            mdlDir = Files.createTempDirectory("analyticsmdls");
            Path analyticsMDLFilePath = mdlDir.resolve("analyticsmdl.json");
            Files.write(analyticsMDLFilePath, MANIFEST_JSON_CODEC.toJsonBytes(manifest));
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        ImmutableMap.Builder<String, String> properties = ImmutableMap.<String, String>builder()
                .put(ANALYTICS_DIRECTORY, mdlDir.toAbsolutePath().toString())
                .put(ANALYTICS_DATASOURCE_TYPE, DUCKDB.name());

        return TestingAnalyticsServer.builder()
                .setRequiredConfigs(properties.build())
                .build();
    }

    @Test
    public void testGetConfigs()
    {
        assertThat(getConfigs().size()).isGreaterThan(2);
        assertThat(getConfig(ANALYTICS_DIRECTORY)).isEqualTo(configEntry(ANALYTICS_DIRECTORY, mdlDir.toAbsolutePath().toString()));
        assertThat(getConfig(ANALYTICS_DATASOURCE_TYPE)).isEqualTo(configEntry(ANALYTICS_DATASOURCE_TYPE, DUCKDB.name()));

        assertThatThrownBy(() -> getConfig("notfound"))
                .hasMessageFindingMatch(".*404 Not Found.*");
        assertThatThrownBy(() -> getConfig(null))
                .hasMessageFindingMatch(".*404 Not Found.*");
    }

    @Test
    public void testuUpdateConfigs()
    {
        patchConfig(List.of(configEntry(DUCKDB_MEMORY_LIMIT, "2GB")));
        assertThat(getConfig(ANALYTICS_DATASOURCE_TYPE)).isEqualTo(configEntry(ANALYTICS_DATASOURCE_TYPE, DUCKDB.name()));
        assertThat(getConfig(ANALYTICS_DIRECTORY)).isEqualTo(configEntry(ANALYTICS_DIRECTORY, mdlDir.toAbsolutePath().toString()));
        assertThat(getConfig(DUCKDB_MEMORY_LIMIT)).isEqualTo(configEntry(DUCKDB_MEMORY_LIMIT, "2GB"));

        resetConfig();
        DuckDBConfig duckDBConfig = new DuckDBConfig();
        AnalyticsConfig analyticsConfig = new AnalyticsConfig();
        assertThat(getConfig(ANALYTICS_DATASOURCE_TYPE)).isEqualTo(configEntry(ANALYTICS_DATASOURCE_TYPE, analyticsConfig.getDataSourceType().name()));
        assertThat(getConfig(ANALYTICS_DIRECTORY)).isEqualTo(configEntry(ANALYTICS_DIRECTORY, analyticsConfig.getAnalyticsMDLDirectory().getPath()));
        assertThat(getConfig(DUCKDB_MEMORY_LIMIT)).isEqualTo(configEntry(DUCKDB_MEMORY_LIMIT, duckDBConfig.getMemoryLimit().toString()));
    }

    @Test
    public void testDataSourceTypeCaseInsensitive()
    {
        patchConfig(List.of(configEntry(ANALYTICS_DATASOURCE_TYPE, "DuCkDb")));
        assertThat(getConfig(ANALYTICS_DATASOURCE_TYPE)).isEqualTo(configEntry(ANALYTICS_DATASOURCE_TYPE, "DuCkDb"));
        AnalyticsConfig analyticsConfig = server().getInstance(Key.get(ConfigManager.class)).getConfig(AnalyticsConfig.class);
        assertThat(analyticsConfig.getDataSourceType()).isEqualTo(DUCKDB);
        patchConfig(List.of(configEntry(ANALYTICS_DATASOURCE_TYPE, DUCKDB.name())));
        assertThat(getConfig(ANALYTICS_DATASOURCE_TYPE)).isEqualTo(configEntry(ANALYTICS_DATASOURCE_TYPE, DUCKDB.name()));
    }

    @Test
    public void testStaticConfigWontBeChanged()
    {
        patchConfig(List.of(
                configEntry(ANALYTICS_DIRECTORY, "fake"),
                configEntry(DUCKDB_MAX_CONCURRENT_TASKS, "100"),
                configEntry(DUCKDB_MAX_CACHE_QUERY_TIMEOUT, "1000"),
                configEntry(DUCKDB_CACHE_TASK_RETRY_DELAY, "1000")));

        DuckDBConfig duckDBConfig = new DuckDBConfig();
        assertThat(getConfig(ANALYTICS_DIRECTORY)).isEqualTo(configEntry(ANALYTICS_DIRECTORY, mdlDir.toAbsolutePath().toString()));
        assertThat(getConfig(DUCKDB_MAX_CONCURRENT_TASKS)).isEqualTo(configEntry(DUCKDB_MAX_CONCURRENT_TASKS, String.valueOf(duckDBConfig.getMaxConcurrentTasks())));
        assertThat(getConfig(DUCKDB_MAX_CACHE_QUERY_TIMEOUT)).isEqualTo(configEntry(DUCKDB_MAX_CACHE_QUERY_TIMEOUT, String.valueOf(duckDBConfig.getMaxCacheQueryTimeout())));
        assertThat(getConfig(DUCKDB_CACHE_TASK_RETRY_DELAY)).isEqualTo(configEntry(DUCKDB_CACHE_TASK_RETRY_DELAY, String.valueOf(duckDBConfig.getCacheTaskRetryDelay())));
    }
}
