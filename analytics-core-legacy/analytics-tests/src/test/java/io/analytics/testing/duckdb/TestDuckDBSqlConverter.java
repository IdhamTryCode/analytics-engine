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

import io.analytics.base.SessionContext;
import io.analytics.base.client.duckdb.DuckDBConfig;
import io.analytics.base.client.duckdb.DuckDBConnectorConfig;
import io.analytics.base.config.ConfigManager;
import io.analytics.base.config.AnalyticsConfig;
import io.analytics.main.connector.duckdb.DuckDBMetadata;
import io.analytics.main.connector.duckdb.DuckDBSqlConverter;
import io.analytics.testing.AbstractSqlConverterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDuckDBSqlConverter
        extends AbstractSqlConverterTest
{
    private static final SessionContext DEFAULT_SESSION_CONTEXT = SessionContext.builder()
            .setCatalog("memory")
            .setSchema("tpch")
            .build();

    private DuckDBSqlConverter sqlConverter;

    @BeforeClass
    public void setup()
            throws Exception
    {
        prepareConfig();

        ConfigManager configManager = new ConfigManager(
                new AnalyticsConfig(),
                new DuckDBConfig(),
                new DuckDBConnectorConfig());

        DuckDBMetadata metadata = new DuckDBMetadata(configManager);

        sqlConverter = new DuckDBSqlConverter(metadata);
    }

    @Test
    public void testArray()
    {
        assertConvert("SELECT ARRAY[1,2,3][1]", "SELECT array_value(1, 2, 3)[1]" + "\n\n");
    }

    @Test
    public void testFunction()
    {
        assertConvert("SELECT generate_array(1, 10)", "SELECT generate_series(1, 10)" + "\n\n");
    }

    @Test
    public void testValues()
    {
        assertConvert("SELECT * FROM (values (ARRAY[1,2,3]))", """
                SELECT *
                FROM
                  (
                 VALUES\s
                     (ARRAY[1,2,3])
                )\s
                """);

        assertConvert("SELECT * FROM (values (1))", """
                SELECT *
                FROM
                  (
                 VALUES\s
                     (1)
                )\s
                """);
        assertConvert("SELECT * FROM (values (1, 2, ARRAY[1,2,3]))", """
                SELECT *
                FROM
                  (
                 VALUES\s
                     (1, 2, ARRAY[1,2,3])
                )\s
                """);
    }

    private void assertConvert(String from, String to)
    {
        assertThat(sqlConverter.convert(from, DEFAULT_SESSION_CONTEXT)).isEqualTo(to);
    }
}
