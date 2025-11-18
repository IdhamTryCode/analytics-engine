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

package io.analytics.base.config;

import io.airlift.configuration.Config;
import jakarta.validation.constraints.NotNull;

import java.io.File;
import java.nio.file.Paths;

public class AnalyticsConfig
{
    public static final String ANALYTICS_DIRECTORY = "analytics.directory";
    public static final String ANALYTICS_DATASOURCE_TYPE = "analytics.datasource.type";
    public static final String ANALYTICS_ENABLE_DYNAMIC_FIELDS = "analytics.experimental-enable-dynamic-fields";

    public enum DataSourceType
    {
        @Deprecated
        BIGQUERY,
        @Deprecated
        POSTGRES,
        DUCKDB,
        @Deprecated
        SNOWFLAKE
    }

    private File analyticsMDLDirectory = Paths.get("etc/mdl").toFile();
    private DataSourceType dataSourceType = DataSourceType.DUCKDB;
    private boolean enableDynamicFields = true;

    @NotNull
    public File getAnalyticsMDLDirectory()
    {
        return analyticsMDLDirectory;
    }

    @Config(ANALYTICS_DIRECTORY)
    public AnalyticsConfig setAnalyticsMDLDirectory(File analyticsMDLDirectory)
    {
        this.analyticsMDLDirectory = analyticsMDLDirectory;
        return this;
    }

    public DataSourceType getDataSourceType()
    {
        return dataSourceType;
    }

    @Config(ANALYTICS_DATASOURCE_TYPE)
    public AnalyticsConfig setDataSourceType(DataSourceType dataSourceType)
    {
        this.dataSourceType = dataSourceType;
        return this;
    }

    public boolean getEnableDynamicFields()
    {
        return enableDynamicFields;
    }

    @Config(ANALYTICS_ENABLE_DYNAMIC_FIELDS)
    public AnalyticsConfig setEnableDynamicFields(boolean enableDynamicFields)
    {
        this.enableDynamicFields = enableDynamicFields;
        return this;
    }
}
