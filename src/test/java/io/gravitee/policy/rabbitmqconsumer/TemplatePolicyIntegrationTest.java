/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
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
import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.endpoint.EndpointConnectorPlugin;
import io.gravitee.apim.gateway.tests.sdk.connector.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.apim.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.apim.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.policy.rabbitmqconsumer.RabbitMQConsumerPolicy;
import io.gravitee.policy.rabbitmqconsumer.configuration.RabbitMQConfiguration;
import java.util.Map;

@GatewayTest
@DeployApi({ "/apis/v4/api.json", "/apis/v4/api-response.json" })
public class TemplatePolicyIntegrationTest extends AbstractPolicyTest<RabbitMQConsumerPolicy, RabbitMQConfiguration> {

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent(
            "http-proxy",
            EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class)
        );
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent(
            "http-proxy",
            EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class)
        );
    }
}
