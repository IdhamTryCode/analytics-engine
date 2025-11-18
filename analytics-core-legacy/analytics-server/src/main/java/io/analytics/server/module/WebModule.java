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

package io.analytics.server.module;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.analytics.main.PreviewService;
import io.analytics.main.ValidationService;
import io.analytics.main.web.AnalysisResource;
import io.analytics.main.web.AnalysisResourceV2;
import io.analytics.main.web.ConfigResource;
import io.analytics.main.web.DuckDBResource;
import io.analytics.main.web.MDLResource;
import io.analytics.main.web.MDLResourceV2;
import io.analytics.main.web.SystemResource;
import io.analytics.main.web.AnalyticsExceptionMapper;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class WebModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        jaxrsBinder(binder).bind(MDLResource.class);
        jaxrsBinder(binder).bind(MDLResourceV2.class);
        jaxrsBinder(binder).bind(AnalysisResource.class);
        jaxrsBinder(binder).bind(AnalysisResourceV2.class);
        jaxrsBinder(binder).bind(ConfigResource.class);
        jaxrsBinder(binder).bind(DuckDBResource.class);
        jaxrsBinder(binder).bind(SystemResource.class);
        jaxrsBinder(binder).bindInstance(new AnalyticsExceptionMapper());
        binder.bind(PreviewService.class).in(Scopes.SINGLETON);
        binder.bind(ValidationService.class).in(Scopes.SINGLETON);
    }
}
