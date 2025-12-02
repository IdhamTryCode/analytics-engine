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

package io.analytics.base.sqlrewrite;

import com.google.common.collect.ImmutableList;
import io.analytics.base.AnalyzedMDL;
import io.analytics.base.SessionContext;
import io.analytics.base.AnalyticsMDL;
import io.analytics.base.AnalyticsTypes;
import io.analytics.base.dto.Column;
import io.analytics.base.dto.JoinType;
import io.analytics.base.dto.Manifest;
import io.analytics.base.dto.Model;
import io.analytics.base.dto.Relationship;
import io.analytics.base.dto.TableReference;
import org.intellij.lang.annotations.Language;
import org.testng.annotations.Test;

import java.util.List;

import static io.analytics.base.sqlrewrite.AnalyticsSqlRewrite.ANALYTICS_SQL_REWRITE;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractTestModel
        extends AbstractTestFramework
{
    protected Model customer;
    protected Model orders;
    protected Model lineitem;

    protected final List<Column> customerColumns;
    protected final List<Column> ordersColumns;
    protected final List<Column> lineitemColumns;
    protected final Relationship ordersCustomer;
    protected final Relationship ordersLineitem;

    public AbstractTestModel()
    {
        customerColumns = List.of(
                Column.column("custkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("name", AnalyticsTypes.VARCHAR, null, true),
                Column.column("address", AnalyticsTypes.VARCHAR, null, true),
                Column.column("nationkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("phone", AnalyticsTypes.VARCHAR, null, true),
                Column.column("acctbal", AnalyticsTypes.INTEGER, null, true),
                Column.column("mktsegment", AnalyticsTypes.VARCHAR, null, true),
                Column.column("comment", AnalyticsTypes.VARCHAR, null, true));
        ordersColumns = List.of(
                Column.column("orderkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("custkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("orderstatus", AnalyticsTypes.VARCHAR, null, true),
                Column.column("totalprice", AnalyticsTypes.INTEGER, null, true),
                Column.column("orderdate", AnalyticsTypes.DATE, null, true),
                Column.column("orderpriority", AnalyticsTypes.VARCHAR, null, true),
                Column.column("clerk", AnalyticsTypes.VARCHAR, null, true),
                Column.column("shippriority", AnalyticsTypes.INTEGER, null, true),
                Column.column("comment", AnalyticsTypes.VARCHAR, null, true),
                Column.column("lineitem", "Lineitem", "OrdersLineitem", true));
        lineitemColumns = List.of(
                Column.column("orderkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("partkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("suppkey", AnalyticsTypes.INTEGER, null, true),
                Column.column("linenumber", AnalyticsTypes.INTEGER, null, true),
                Column.column("quantity", AnalyticsTypes.INTEGER, null, true),
                Column.column("extendedprice", AnalyticsTypes.INTEGER, null, true),
                Column.column("discount", AnalyticsTypes.INTEGER, null, true),
                Column.column("tax", AnalyticsTypes.INTEGER, null, true),
                Column.column("returnflag", AnalyticsTypes.VARCHAR, null, true),
                Column.column("linestatus", AnalyticsTypes.VARCHAR, null, true),
                Column.column("shipdate", AnalyticsTypes.DATE, null, true),
                Column.column("commitdate", AnalyticsTypes.DATE, null, true),
                Column.column("receiptdate", AnalyticsTypes.DATE, null, true),
                Column.column("shipinstruct", AnalyticsTypes.VARCHAR, null, true),
                Column.column("shipmode", AnalyticsTypes.VARCHAR, null, true),
                Column.column("comment", AnalyticsTypes.VARCHAR, null, true),
                Column.column("orderkey_linenumber", AnalyticsTypes.VARCHAR, null, true, "concat(orderkey, '-', linenumber)"));
        ordersCustomer = Relationship.relationship("OrdersCustomer", List.of("Orders", "Customer"), JoinType.MANY_TO_ONE, "Orders.custkey = Customer.custkey");
        ordersLineitem = Relationship.relationship("OrdersLineitem", List.of("Orders", "Lineitem"), JoinType.ONE_TO_MANY, "Orders.orderkey = Lineitem.orderkey");
    }

    @Override
    protected void prepareData()
    {
        String orders = requireNonNull(getClass().getClassLoader().getResource("tiny-orders.parquet")).getPath();
        exec("create table orders as select * from '" + orders + "'");
        String customer = requireNonNull(getClass().getClassLoader().getResource("tiny-customer.parquet")).getPath();
        exec("create table customer as select * from '" + customer + "'");
        String lineitem = requireNonNull(getClass().getClassLoader().getResource("tiny-lineitem.parquet")).getPath();
        exec("create table lineitem as select * from '" + lineitem + "'");
    }

    @Test
    public void testToManyCalculated()
    {
        // TODO: add this to test case, currently this won't work
        // caluclatedColumn("col_3", BIGINT, "concat(address, sum(orders.lineitem.discount * orders.lineitem.extendedprice))");

        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("totalprice", AnalyticsTypes.BIGINT, "sum(orders.totalprice)"),
                Column.calculatedColumn("buy_item_count", AnalyticsTypes.BIGINT, "count(distinct orders.lineitem.orderkey_linenumber)"),
                Column.calculatedColumn("lineitem_totalprice", AnalyticsTypes.BIGINT, "sum(orders.lineitem.discount * orders.lineitem.extendedprice)"),
                Column.calculatedColumn("test_col", AnalyticsTypes.BIGINT, "sum(orders.lineitem.discount * nationkey)"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, orders, lineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);

        assertQuery(mdl,
                "SELECT totalprice FROM Customer WHERE custkey = 370",
                "SELECT sum(totalprice) FROM customer c LEFT JOIN orders o ON c.custkey = o.custkey WHERE c.custkey = 370");
        assertQuery(mdl,
                "SELECT custkey, buy_item_count FROM Customer WHERE custkey = 370",
                "SELECT c.custkey, count(*) FROM customer c " +
                        "LEFT JOIN orders o ON c.custkey = o.custkey " +
                        "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                        "WHERE c.custkey = 370 " +
                        "GROUP BY 1");
        assertQuery(mdl,
                "SELECT custkey, lineitem_totalprice FROM Customer WHERE custkey = 370",
                "SELECT c.custkey, sum(l.extendedprice * l.discount) FROM customer c " +
                        "LEFT JOIN orders o ON c.custkey = o.custkey " +
                        "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                        "WHERE c.custkey = 370 " +
                        "GROUP BY 1");

        assertQuery(mdl,
                "SELECT custkey, test_col FROM Customer WHERE custkey = 370",
                "SELECT c.custkey, sum(l.discount * c.nationkey) FROM customer c " +
                        "LEFT JOIN orders o ON c.custkey = o.custkey " +
                        "LEFT JOIN lineitem l ON o.orderkey = l.orderkey " +
                        "WHERE c.custkey = 370 " +
                        "GROUP BY 1");
    }

    @Test
    public void testToOneCalculated()
    {
        Model newLineitem = addColumnsToModel(
                lineitem,
                Column.column("orders", "Orders", "OrdersLineitem", true),
                Column.calculatedColumn("col_1", AnalyticsTypes.BIGINT, "orders.totalprice + orders.totalprice"),
                Column.calculatedColumn("col_2", AnalyticsTypes.BIGINT, "concat(orders.orderkey, '#', orders.customer.custkey)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);

        assertQuery(mdl,
                "SELECT col_1 FROM Lineitem WHERE orderkey = 44995",
                "SELECT (totalprice + totalprice) AS col_1\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "WHERE l.orderkey = 44995");
        assertQuery(mdl,
                "SELECT col_1 FROM Lineitem WHERE orderkey = 44995",
                "SELECT (totalprice + totalprice) AS col_1\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "WHERE l.orderkey = 44995");
        assertQuery(mdl,
                "SELECT col_2 FROM Lineitem WHERE orderkey = 44995",
                "SELECT concat(l.orderkey, '#', c.custkey) AS col_2\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                        "WHERE l.orderkey = 44995");

        assertQuery(mdl, "SELECT count(*) FROM Lineitem", "SELECT count(*) FROM lineitem");
        assertQuery(mdl, "SELECT count(*) FROM Lineitem WHERE orderkey = 44995",
                "SELECT count(*) FROM lineitem WHERE orderkey = 44995");
        assertQuery(mdl, "SELECT count(*) FROM Lineitem l WHERE l.orderkey = 44995",
                "SELECT count(*) FROM lineitem l WHERE l.orderkey = 44995");

        assertQuery(mdl, "SELECT col_1 FROM Lineitem ORDER BY col_2", "SELECT (totalprice + totalprice) AS col_1\n" +
                "FROM lineitem l\n" +
                "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                "ORDER BY concat(l.orderkey, '#', c.custkey)");
        assertQuery(mdl, "SELECT count(*) FROM Lineitem group by col_1, col_2 order by 1", "SELECT count(*)\n" +
                "FROM lineitem l\n" +
                "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                "LEFT JOIN customer c ON o.custkey = c.custkey\n" +
                "GROUP BY (totalprice + totalprice), concat(l.orderkey, '#', c.custkey)\n" +
                "ORDER BY 1");
        assertQuery(mdl, "SELECT rank() over (order by col_1) FROM Lineitem",
                "SELECT rank() OVER (ORDER BY (totalprice + totalprice))\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey");
        assertQuery(mdl, "SELECT count(f1) FROM (SELECT lag(extendedprice) over (partition by col_2) as f1 FROM Lineitem)",
                "SELECT count(f1) FROM (SELECT lag(extendedprice) OVER (PARTITION BY concat(l.orderkey, '#', c.custkey)) as f1\n" +
                        "FROM lineitem l\n" +
                        "LEFT JOIN orders o ON l.orderkey = o.orderkey\n" +
                        "LEFT JOIN customer c ON o.custkey = c.custkey)");
    }

    @Test
    public void testModelWithCycle()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("total_price", AnalyticsTypes.BIGINT, "sum(orders.totalprice)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true),
                Column.calculatedColumn("customer_name", AnalyticsTypes.BIGINT, "customer.name"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, newOrders))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);

        assertThatCode(() -> query(rewrite("SELECT * FROM Orders", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT customer_name FROM Orders WHERE orderkey = 44995", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT total_price FROM Customer", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT total_price FROM Customer c LEFT JOIN Orders o ON c.custkey = o.custkey", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT o.custkey, total_price FROM Customer c LEFT JOIN Orders o ON c.custkey = o.custkey", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("SELECT customer_name, total_price FROM Customer c LEFT JOIN Orders o ON c.custkey = o.custkey", mdl, true)))
                .hasMessageMatching("found cycle in .*");
    }

    @Test
    public void testModelOnModel()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("totalprice", AnalyticsTypes.BIGINT, "sum(orders.totalprice)"));
        Model onCustomer = Model.onBaseObject(
                "OnCustomer",
                "Customer",
                ImmutableList.of(
                        Column.column("mom_custkey", "VARCHAR", null, true, "custkey"),
                        Column.column("mom_totalprice", "VARCHAR", null, true, "totalprice")),
                "mom_custkey");
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, onCustomer, orders))
                .setRelationships(List.of(ordersCustomer))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);

        assertQuery(mdl, "SELECT mom_custkey, mom_totalprice FROM OnCustomer WHERE mom_custkey = 370",
                "SELECT c.custkey, sum(o.totalprice) FROM customer c\n" +
                        "LEFT JOIN orders o ON c.custkey = o.custkey\n" +
                        "WHERE c.custkey = 370\n" +
                        "GROUP BY 1");

        assertThatCode(() -> query(rewrite("SELECT 1 FROM OnCustomer", mdl, true)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testCalculatedUseAnotherCalculated()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("total_price", AnalyticsTypes.BIGINT, "sum(orders.totalprice)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true),
                Column.column("lineitem", "Lineitem", "OrdersLineitem", true),
                Column.calculatedColumn("customer_name", AnalyticsTypes.BIGINT, "customer.name"),
                Column.calculatedColumn("extended_price", AnalyticsTypes.BIGINT, "sum(lineitem.extendedprice)"));
        Model newLineitem = addColumnsToModel(
                lineitem,
                Column.column("orders", "Orders", "OrdersLineitem", true),
                Column.calculatedColumn("test_column", AnalyticsTypes.BIGINT, "orders.customer.total_price + extendedprice"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);

        assertThatCode(() -> query(rewrite("SELECT test_column FROM Lineitem", mdl, true)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testSelectEmpty()
    {
        Model newCustomer = addColumnsToModel(
                customer,
                Column.column("orders", "Orders", "OrdersCustomer", true),
                Column.calculatedColumn("total_price", AnalyticsTypes.BIGINT, "sum(orders.totalprice)"));
        Model newOrders = addColumnsToModel(
                orders,
                Column.column("customer", "Customer", "OrdersCustomer", true),
                Column.column("lineitem", "Lineitem", "OrdersLineitem", true),
                Column.calculatedColumn("customer_name", AnalyticsTypes.BIGINT, "customer.name"),
                Column.calculatedColumn("extended_price", AnalyticsTypes.BIGINT, "sum(lineitem.extendedprice)"));
        Model newLineitem = addColumnsToModel(
                lineitem,
                Column.column("orders", "Orders", "OrdersLineitem", true),
                Column.calculatedColumn("test_column", AnalyticsTypes.BIGINT, "orders.customer.total_price + extendedprice"));
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(newCustomer, newOrders, newLineitem))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);

        assertThatCode(() -> query(rewrite("SELECT true \"_\" FROM Lineitem", mdl, true)))
                .doesNotThrowAnyException();

        assertThatCode(() -> query(rewrite("SELECT true \"_\" FROM Lineitem, Orders", mdl, true)))
                .doesNotThrowAnyException();

        assertThatCode(() -> query(rewrite("SELECT orderkey FROM Lineitem, Orders", mdl, true)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testCustomCTE()
    {
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer, orders, lineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);
        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders) SELECT * FROM cte", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders) SELECT * FROM cte", mdl, false)))
                .doesNotThrowAnyException();

        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders), cte2 as (SELECT * FROM cte) SELECT * FROM cte2", mdl, true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> query(rewrite("WITH cte AS (SELECT * FROM Orders), cte2 as (SELECT * FROM cte) SELECT * FROM cte2", mdl, false)))
                .doesNotThrowAnyException();
    }

    @Test
    public void testSelectNotFound()
    {
        Manifest manifest = withDefaultCatalogSchema()
                .setModels(List.of(customer))
                .setRelationships(List.of(ordersCustomer, ordersLineitem))
                .build();
        AnalyticsMDL mdl = AnalyticsMDL.fromManifest(manifest);
        assertThatThrownBy(() -> query(rewrite("SELECT * FROM notfound", mdl, true)))
                .hasMessageFindingMatch(".*notfound.*");
    }

    @Test
    public void testBuildModelFailed()
    {
        assertThatThrownBy(() -> buildFailedModel("select * from main.orders", "Orders", TableReference.tableReference("memory", "main", "orders")))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel(null, "Orders", TableReference.tableReference("memory", "main", "orders")))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel("select * from main.orders", null, TableReference.tableReference("memory", "main", "orders")))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel("select * from main.orders", "Orders", null))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
        assertThatThrownBy(() -> buildFailedModel(null, null, null))
                .hasMessageContaining("either none or more than one of (refSql, baseObject, tableReference) are set");
    }

    private void buildFailedModel(String refSql, String baseObject, TableReference tableReference)
    {
        new Model("failed", refSql, baseObject, tableReference, null, null, false, null);
    }

    private void assertQuery(AnalyticsMDL mdl, @Language("SQL") String analyticsSql, @Language("SQL") String duckDBSql)
    {
        assertThat(query(rewrite(analyticsSql, mdl, true))).isEqualTo(query(duckDBSql));
        assertThat(query(rewrite(analyticsSql, mdl, false))).isEqualTo(query(duckDBSql));
    }

    private String rewrite(String sql, AnalyticsMDL analyticsMDL, boolean enableDynamicField)
    {
        SessionContext sessionContext = SessionContext.builder()
                .setCatalog("analytics")
                .setSchema("test")
                .setEnableDynamic(enableDynamicField)
                .build();
        return AnalyticsPlanner.rewrite(sql, sessionContext, new AnalyzedMDL(analyticsMDL, null), List.of(ANALYTICS_SQL_REWRITE));
    }
}
