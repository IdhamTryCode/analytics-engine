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
import io.analytics.base.dto.Column;
import io.analytics.base.dto.JoinType;
import io.analytics.base.dto.Manifest;
import io.analytics.main.validation.ColumnIsValid;
import io.analytics.main.validation.ValidationResult;
import io.analytics.main.web.dto.DryPlanDto;
import io.analytics.main.web.dto.PreviewDto;
import io.analytics.main.web.dto.QueryResultDto;
import io.analytics.main.web.dto.ValidateDto;
import org.testng.annotations.Test;

import java.util.List;

import static io.analytics.base.config.AnalyticsConfig.DataSourceType.DUCKDB;
import static io.analytics.base.config.AnalyticsConfig.ANALYTICS_DATASOURCE_TYPE;
import static io.analytics.base.config.AnalyticsConfig.ANALYTICS_ENABLE_DYNAMIC_FIELDS;
import static io.analytics.base.dto.Column.calculatedColumn;
import static io.analytics.base.dto.Column.column;
import static io.analytics.base.dto.Model.model;
import static io.analytics.base.dto.Relationship.relationship;
import static io.analytics.main.validation.ColumnIsValid.COLUMN_IS_VALID;
import static io.analytics.testing.WebApplicationExceptionAssert.assertWebApplicationException;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMDLResource
        extends RequireAnalyticsServer
{
    private Manifest manifest = Manifest.builder()
            .setCatalog("analyticsai")
            .setSchema("tpch")
            .setModels(List.of(
                    model("Orders", "SELECT * FROM tpch.orders", List.of(column("orderkey", "integer", null, false, "o_orderkey")))))
            .build();

    @Override
    protected TestingAnalyticsServer createAnalyticsServer()
            throws Exception
    {
        ImmutableMap.Builder<String, String> properties = ImmutableMap.<String, String>builder()
                .put(ANALYTICS_DATASOURCE_TYPE, DUCKDB.name())
                .put(ANALYTICS_ENABLE_DYNAMIC_FIELDS, "true");

        return TestingAnalyticsServer.builder()
                .setRequiredConfigs(properties.build())
                .build();
    }

    @Override
    protected void prepare()
    {
        initDuckDB();
    }

    @Test
    public void testPreview()
    {
        Manifest previewManifest = Manifest.builder()
                .setCatalog("analyticsai")
                .setSchema("tpch")
                .setModels(List.of(
                        model("Customer", "SELECT * FROM tpch.customer",
                                List.of(column("custkey", "integer", null, false, "c_custkey")))))
                .build();

        PreviewDto testDefaultDto = new PreviewDto(previewManifest, "select custkey from Customer", null);
        QueryResultDto testDefault = preview(testDefaultDto);
        assertThat(testDefault.getData().size()).isEqualTo(100);
        assertThat(testDefault.getColumns().size()).isEqualTo(1);
        assertThat(testDefault.getColumns().get(0).getName()).isEqualTo("custkey");
        assertThat(testDefault.getColumns().get(0).getType()).isEqualTo("INTEGER");

        PreviewDto testDefaultDto1 = new PreviewDto(previewManifest, "select custkey from Customer limit 200", null);
        QueryResultDto preview1 = preview(testDefaultDto1);
        assertThat(preview1.getData().size()).isEqualTo(100);
        assertThat(preview1.getColumns().size()).isEqualTo(1);

        PreviewDto testDefaultDto2 = new PreviewDto(previewManifest, "select custkey from Customer limit 200", 150L);
        QueryResultDto preview2 = preview(testDefaultDto2);
        assertThat(preview2.getData().size()).isEqualTo(150);
        assertThat(preview2.getColumns().size()).isEqualTo(1);

        assertWebApplicationException(() -> preview(new PreviewDto(previewManifest, "select orderkey from Orders limit 100", null)))
                .hasErrorMessageMatches("(?s).*Orders does not exist.*");
    }

    @Test
    public void testDryRunAndDryPlan()
    {
        Manifest previewManifest = Manifest.builder()
                .setCatalog("analyticsai")
                .setSchema("tpch")
                .setModels(List.of(
                        model("Customer", "SELECT * FROM tpch.customer",
                                List.of(column("custkey", "integer", null, false, "c_custkey"),
                                        column("name", "varchar", null, false, "c_name"))),
                        model("Orders", "SELECT * FROM tpch.orders",
                                List.of(column("orderkey", "integer", null, false, "o_orderkey"),
                                        column("custkey", "integer", null, false, "o_custkey"),
                                        column("customer", "Customer", "CustomerOrders", false),
                                        calculatedColumn("customer_name", "varchar", "customer.name")),
                                "orderkey")))
                .setRelationships(List.of(relationship("CustomerOrders", List.of("Customer", "Orders"), JoinType.ONE_TO_MANY, "Customer.custkey = Orders.custkey")))
                .build();

        PreviewDto testDefaultDto1 = new PreviewDto(previewManifest, "select orderkey from Orders limit 200", null);
        List<Column> dryRun = dryRun(testDefaultDto1);
        assertThat(dryRun.size()).isEqualTo(1);
        assertThat(dryRun.get(0).getName()).isEqualTo("orderkey");

        DryPlanDto dryPlanDto = new DryPlanDto(previewManifest, "select orderkey from Orders limit 200", false);
        String dryPlan = dryPlan(dryPlanDto);
        assertThat(dryPlan).isEqualTo("""
                WITH
                  "Orders" AS (
                   SELECT
                     "Orders"."orderkey" "orderkey"
                   , "Orders"."custkey" "custkey"
                   FROM
                     (
                      SELECT
                        "Orders"."orderkey" "orderkey"
                      , "Orders"."custkey" "custkey"
                      FROM
                        (
                         SELECT
                           o_orderkey "orderkey"
                         , o_custkey "custkey"
                         FROM
                           (
                            SELECT *
                            FROM
                              tpch.orders
                         )  "Orders"
                      )  "Orders"
                   )  "Orders"
                )\s
                SELECT orderkey
                FROM
                  Orders
                LIMIT 200
                """);

        dryPlanDto = new DryPlanDto(previewManifest, "select orderkey from Orders limit 200", true);
        dryPlan = dryPlan(dryPlanDto);
        assertThat(dryPlan).isEqualTo("""
                WITH
                  "Orders" AS (
                   SELECT
                     "Orders"."orderkey" "orderkey"
                   , "Orders"."custkey" "custkey"
                   FROM
                     (
                      SELECT
                        "Orders"."orderkey" "orderkey"
                      , "Orders"."custkey" "custkey"
                      FROM
                        (
                         SELECT
                           o_orderkey "orderkey"
                         , o_custkey "custkey"
                         FROM
                           (
                            SELECT *
                            FROM
                              tpch.orders
                         )  "Orders"
                      )  "Orders"
                   )  "Orders"
                )\s
                SELECT orderkey
                FROM
                  Orders
                LIMIT 200
                """);

        dryPlanDto = new DryPlanDto(previewManifest, "select customer_name from Orders limit 200", false);
        dryPlan = dryPlan(dryPlanDto);
        assertThat(dryPlan).isEqualTo("""
                WITH
                  "Customer" AS (
                   SELECT
                     "Customer"."custkey" "custkey"
                   , "Customer"."name" "name"
                   FROM
                     (
                      SELECT
                        "Customer"."custkey" "custkey"
                      , "Customer"."name" "name"
                      FROM
                        (
                         SELECT
                           c_custkey "custkey"
                         , c_name "name"
                         FROM
                           (
                            SELECT *
                            FROM
                              tpch.customer
                         )  "Customer"
                      )  "Customer"
                   )  "Customer"
                )\s
                , "Orders" AS (
                   SELECT
                     "Orders"."orderkey" "orderkey"
                   , "Orders"."custkey" "custkey"
                   , "Orders_relationsub"."customer_name" "customer_name"
                   FROM
                     (
                      SELECT
                        "Orders"."orderkey" "orderkey"
                      , "Orders"."custkey" "custkey"
                      FROM
                        (
                         SELECT
                           o_orderkey "orderkey"
                         , o_custkey "custkey"
                         FROM
                           (
                            SELECT *
                            FROM
                              tpch.orders
                         )  "Orders"
                      )  "Orders"
                   )  "Orders"
                   LEFT JOIN (
                      SELECT
                        "Orders"."orderkey"
                      , "Customer"."name" "customer_name"
                      FROM
                        (
                         SELECT
                           o_orderkey "orderkey"
                         , o_custkey "custkey"
                         FROM
                           (
                            SELECT *
                            FROM
                              tpch.orders
                         )  "Orders"
                      )  "Orders"
                      LEFT JOIN "Customer" ON ("Customer"."custkey" = "Orders"."custkey")
                   )  "Orders_relationsub" ON ("Orders"."orderkey" = "Orders_relationsub"."orderkey")
                )\s
                SELECT customer_name
                FROM
                  Orders
                LIMIT 200
                """);
    }

    @Test
    public void testValidation()
    {
        List<ValidationResult> validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters("Orders", "orderkey")));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid:Orders:orderkey");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.PASS);

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters("Orders", "notfound")));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid:Orders:notfound");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.FAIL);
        assertThat(validations.getFirst().getMessage()).isNotEmpty();

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters(null, "orderkey")));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.ERROR);
        assertThat(validations.getFirst().getMessage()).isEqualTo("Model name is required");

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters("", "orderkey")));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.ERROR);
        assertThat(validations.getFirst().getMessage()).isEqualTo("Model name is required");

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters("Orders", null)));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid:Orders");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.ERROR);
        assertThat(validations.getFirst().getMessage()).isEqualTo("Column name is required");

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters("Orders", "")));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid:Orders");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.ERROR);
        assertThat(validations.getFirst().getMessage()).isEqualTo("Column name is required");

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, ColumnIsValid.parameters(null, null)));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.ERROR);
        assertThat(validations.getFirst().getMessage()).isEqualTo("Model name is required");

        validations = validate(COLUMN_IS_VALID, new ValidateDto(manifest, null));
        assertThat(validations.size()).isEqualTo(1);
        assertThat(validations.getFirst().getName()).isEqualTo("column_is_valid");
        assertThat(validations.getFirst().getStatus()).isEqualTo(ValidationResult.Status.ERROR);
        assertThat(validations.getFirst().getMessage()).isEqualTo("Model name is required");

        assertWebApplicationException(() ->  validate(COLUMN_IS_VALID, null))
                .hasErrorMessageMatches(".*Manifest is required.*");
    }
}
