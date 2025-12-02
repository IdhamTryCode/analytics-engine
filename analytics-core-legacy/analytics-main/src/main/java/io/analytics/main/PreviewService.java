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
package io.analytics.main;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.analytics.base.AnalyzedMDL;
import io.analytics.base.Column;
import io.analytics.base.ConnectorRecordIterator;
import io.analytics.base.SessionContext;
import io.analytics.base.AnalyticsMDL;
import io.analytics.base.client.duckdb.DuckDBConfig;
import io.analytics.base.config.ConfigManager;
import io.analytics.base.config.AnalyticsConfig;
import io.analytics.base.dto.JoinType;
import io.analytics.base.dto.Manifest;
import io.analytics.base.dto.Model;
import io.analytics.base.sql.SqlConverter;
import io.analytics.base.sqlrewrite.AnalyticsPlanner;
import io.analytics.main.metadata.Metadata;
import io.analytics.main.web.dto.QueryResultDto;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.analytics.base.dto.Column.calculatedColumn;
import static io.analytics.base.dto.Column.column;
import static io.analytics.base.dto.Column.relationshipColumn;
import static io.analytics.base.dto.Model.model;
import static io.analytics.base.dto.Relationship.relationship;
import static io.analytics.base.dto.TableReference.tableReference;
import static io.analytics.base.dto.View.view;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

public class PreviewService
{
    private static final Logger LOG = Logger.get(PreviewService.class);
    private final Metadata metadata;

    private final SqlConverter sqlConverter;
    private final ConfigManager configManager;
    private final ExecutorService connectionPool;

    private boolean isWarmed;

    @Inject
    public PreviewService(
            Metadata metadata,
            SqlConverter sqlConverter,
            ConfigManager configManager)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.sqlConverter = requireNonNull(sqlConverter, "sqlConverter is null");
        this.configManager = requireNonNull(configManager, "configManager is null");
        DuckDBConfig config = configManager.getConfig(DuckDBConfig.class);
        this.connectionPool = Executors.newFixedThreadPool(config.getMaxConcurrentTasks());
    }

    public CompletableFuture<QueryResultDto> preview(AnalyticsMDL mdl, String sql, long limit)
    {
        return CompletableFuture.supplyAsync(() -> {
            AnalyticsConfig config = configManager.getConfig(AnalyticsConfig.class);
            SessionContext sessionContext = SessionContext.builder()
                    .setCatalog(mdl.getCatalog())
                    .setSchema(mdl.getSchema())
                    .setEnableDynamic(config.getEnableDynamicFields())
                    .build();

            String planned = AnalyticsPlanner.rewrite(sql, sessionContext, new AnalyzedMDL(mdl, null));
            String converted = sqlConverter.convert(planned, sessionContext);
            try (ConnectorRecordIterator iter = metadata.directQuery(converted, List.of())) {
                return new QueryResultDto(
                        iter.getColumns(),
                        Streams.stream(iter).limit(limit).collect(toList()));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, connectionPool);
    }

    public CompletableFuture<String> dryPlan(AnalyticsMDL mdl, String sql, boolean isModelingOnly)
    {
        return CompletableFuture.supplyAsync(() -> {
            AnalyticsConfig config = configManager.getConfig(AnalyticsConfig.class);
            SessionContext sessionContext = SessionContext.builder()
                    .setCatalog(mdl.getCatalog())
                    .setSchema(mdl.getSchema())
                    .setEnableDynamic(config.getEnableDynamicFields())
                    .build();

            String planned = AnalyticsPlanner.rewrite(sql, sessionContext, new AnalyzedMDL(mdl, null));
            if (isModelingOnly) {
                LOG.info("Planned SQL: %s", planned);
                return planned;
            }
            return sqlConverter.convert(planned, sessionContext);
        }, connectionPool);
    }

    public CompletableFuture<List<Column>> dryRun(AnalyticsMDL mdl, String sql)
    {
        return CompletableFuture.supplyAsync(() -> {
            AnalyticsConfig config = configManager.getConfig(AnalyticsConfig.class);
            SessionContext sessionContext = SessionContext.builder()
                    .setCatalog(mdl.getCatalog())
                    .setSchema(mdl.getSchema())
                    .setEnableDynamic(config.getEnableDynamicFields())
                    .build();

            String planned = AnalyticsPlanner.rewrite(sql, sessionContext, new AnalyzedMDL(mdl, null));
            String converted = sqlConverter.convert(planned, sessionContext);
            return metadata.describeQuery(converted, List.of());
        }, connectionPool);
    }

    public boolean isWarmed()
    {
        return isWarmed;
    }

    public void warmUp()
    {
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(
                Manifest.builder()
                        .setCatalog("default")
                        .setSchema("default")
                        .setModels(ImmutableList.of(
                                model("Orders", "SELECT * FROM tpch.tiny.orders",
                                        ImmutableList.of(
                                                column("orderkey", "decimal", null, false),
                                                column("custkey", "decimal", null, false),
                                                relationshipColumn("customer", "Customer", "OrdersCustomer"),
                                                calculatedColumn("double_key", "decimal", "orderkey * 2"),
                                                calculatedColumn("customer_key", "decimal", "customer.custkey"))),
                                Model.onTableReference("Customer",
                                        tableReference("tpch", "tiny", "customer"),
                                        ImmutableList.of(
                                                column("custkey", "decimal", null, false)),
                                        null)))
                        .setRelationships(ImmutableList.of(
                                relationship("OrdersCustomer",
                                        ImmutableList.of("Orders", "Customer"),
                                        JoinType.MANY_TO_ONE,
                                        "Orders.custkey = Customer.custkey")))
                        .setViews(ImmutableList.of(view("customer_view", "SELECT * FROM Customer")))
                        .build());
        dryPlan(mdl, "SELECT orderkey, double_key, customer_key FROM Orders", true)
                .thenRun(() -> {
                    isWarmed = true;
                    LOG.info("Warm up done");
                })
                .join();
    }
}
