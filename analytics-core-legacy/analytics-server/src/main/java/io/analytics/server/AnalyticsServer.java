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

package io.analytics.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import io.analytics.main.PreviewService;
import io.analytics.main.AnalyticsModule;
import io.analytics.main.server.Server;
import io.analytics.server.module.DuckDBConnectorModule;
import io.analytics.server.module.MainModule;
import io.analytics.server.module.OpenTelemetryModule;
import io.analytics.server.module.WebModule;

public class AnalyticsServer
        extends Server
{
    public static void main(String[] args)
    {
        new AnalyticsServer().start();
    }

    @Override
    protected Iterable<? extends Module> getAdditionalModules()
    {
        return ImmutableList.of(
                new NodeModule(),
                new HttpServerModule(),
                new JsonModule(),
                new JaxrsModule(),
                new OpenTelemetryModule(),
                new MainModule(),
                new DuckDBConnectorModule(),
                new AnalyticsModule(),
                new WebModule());
    }

    @Override
    protected void configure(Injector injector)
    {
        warmUp(injector);
    }

    private void warmUp(Injector injector)
    {
        injector.getInstance(PreviewService.class).warmUp();
    }
}
