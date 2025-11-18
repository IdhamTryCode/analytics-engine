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

package io.analytics.testing.duckdb;

import com.google.common.collect.ImmutableMap;
import io.analytics.base.dto.Manifest;
import io.analytics.testing.AbstractFunctionTest;
import io.analytics.testing.TestingAnalyticsServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.analytics.base.config.AnalyticsConfig.DataSourceType.DUCKDB;
import static io.analytics.base.config.AnalyticsConfig.ANALYTICS_DATASOURCE_TYPE;
import static io.analytics.base.config.AnalyticsConfig.ANALYTICS_DIRECTORY;

public class TestFunctionDuckDB
        extends AbstractFunctionTest
{
    @Override
    protected TestingAnalyticsServer createAnalyticsServer()
    {
        Path mdlDir;

        try {
            mdlDir = Files.createTempDirectory("analyticsmdls");
            Path analyticsMDLFilePath = mdlDir.resolve("analyticsmdl.json");
            Files.write(analyticsMDLFilePath, MANIFEST_JSON_CODEC.toJsonBytes(Manifest.builder().setCatalog("analytics").setSchema("test").build()));
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
}
