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

package io.analytics.base.sqlrewrite.analyzer;

import io.trino.sql.tree.AliasedRelation;
import io.trino.sql.tree.DefaultTraversalVisitor;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.TableSubquery;
import io.analytics.base.CatalogSchemaTableName;
import io.analytics.base.SessionContext;
import io.analytics.base.AnalyticsMDL;
import io.analytics.base.sqlrewrite.Utils;

public class ScopeAnalyzer
{
    private ScopeAnalyzer() {}

    public static ScopeAnalysis analyze(AnalyticsMDL analyticsMDL, Node node, SessionContext sessionContext)
    {
        ScopeAnalysis analysis = new ScopeAnalysis();
        Visitor visitor = new Visitor(analyticsMDL, analysis, sessionContext);
        visitor.process(node, null);
        return analysis;
    }

    static class Visitor
            extends DefaultTraversalVisitor<Void>
    {
        private final AnalyticsMDL analyticsMDL;
        private final ScopeAnalysis analysis;
        private final SessionContext sessionContext;

        public Visitor(AnalyticsMDL analyticsMDL, ScopeAnalysis analysis, SessionContext sessionContext)
        {
            this.analyticsMDL = analyticsMDL;
            this.analysis = analysis;
            this.sessionContext = sessionContext;
        }

        @Override
        protected Void visitTable(Table node, Void context)
        {
            if (isBelongToAnalytics(node.getName())) {
                analysis.addUsedAnalyticsObject(node);
            }
            return null;
        }

        @Override
        protected Void visitTableSubquery(TableSubquery node, Void context)
        {
            return null;
        }

        @Override
        protected Void visitAliasedRelation(AliasedRelation node, Void context)
        {
            analysis.addAliasedNode(node.getRelation(), node.getAlias().getValue());
            return super.visitAliasedRelation(node, context);
        }

        private boolean isBelongToAnalytics(QualifiedName analyticsObjectName)
        {
            CatalogSchemaTableName catalogSchemaTableName = Utils.toCatalogSchemaTableName(sessionContext, analyticsObjectName);
            String tableName = catalogSchemaTableName.getSchemaTableName().getTableName();
            return catalogSchemaTableName.getCatalogName().equals(analyticsMDL.getCatalog())
                    && catalogSchemaTableName.getSchemaTableName().getSchemaName().equals(analyticsMDL.getSchema())
                    && (analyticsMDL.listModels().stream().anyMatch(model -> model.getName().equals(tableName))
                    || analyticsMDL.listMetrics().stream().anyMatch(metric -> metric.getName().equals(tableName)));
        }
    }
}
