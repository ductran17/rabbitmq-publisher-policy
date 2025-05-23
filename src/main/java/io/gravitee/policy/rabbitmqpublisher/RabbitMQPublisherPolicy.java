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
package io.gravitee.policy.rabbitmqpublisher;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.rabbitmqpublisher.configuration.RabbitMQConfiguration;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class RabbitMQPublisherPolicy implements Policy {

    private final RabbitMQConfiguration configuration;
    private final ConnectionFactory factory;
    private Integer timeToLive;
    Map<String, Boolean> queueConfig = new HashMap<>();
    private String attributeQueueID;
    private Boolean createQueue;
    private Boolean publishQueue;
    private String body;

    public RabbitMQPublisherPolicy(RabbitMQConfiguration configuration) {
        this.configuration = configuration;
        this.factory = new ConnectionFactory();
        factory.setHost(configuration.getHost());
        factory.setPort(configuration.getPort());
        factory.setUsername(configuration.getUsername());
        factory.setPassword(configuration.getPassword());
        factory.setVirtualHost("/");
        factory.setConnectionTimeout(5000);
        this.attributeQueueID = configuration.getAttributeQueueID();
        this.timeToLive = configuration.getTimeToLive();
        this.createQueue = configuration.getCreateQueue();
        this.publishQueue = configuration.getConsumeQueue();
        this.queueConfig =
            Map.of(
                "durable",
                configuration.getQueueDurable(),
                "exclusive",
                configuration.getQueueExclusive(),
                "autoDelete",
                configuration.getQueueAutoDelete()
            );
        this.body = configuration.getBody();
    }

    @Override
    public String id() {
        return "rabbitmq-publisher-policy";
    }

    @Override
    public Completable onRequest(HttpExecutionContext ctx) {
        return Completable.create(emitter -> {
            String subscriptionId = ctx.getAttribute(this.attributeQueueID);
            if (subscriptionId == null) {
                emitter.onError(new IllegalArgumentException("Subscription ID not found in context"));
                return;
            }

            try {
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();

                if (this.createQueue) {
                    try {
                        // Use queueDeclare to make sure queue exist (will create if queue not exist
                        channel.queueDeclare(
                            subscriptionId,
                            queueConfig.get("durable"), // durable
                            queueConfig.get("exclusive"), // exclusive
                            queueConfig.get("autoDelete"), // autoDelete
                            Map.of("x-expires", this.timeToLive)
                        );
                    } catch (IOException e) {
                        emitter.onError(
                            new RuntimeException("Queue declaration failed. Possibly due to mismatched parameters.", e)
                        );
                        return;
                    }
                }

                // Consume messages and stop after receiving the first one
                if (this.publishQueue) {
                    // 1. Setup Freemarker configuration
                    Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
                    cfg.setIncompatibleImprovements(Configuration.VERSION_2_3_31);
                    cfg.setDefaultEncoding("UTF-8");
                    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

                    // Validate body is not null or empty
                    if (body == null || body.trim().isEmpty()) {
                        log.error("Message body template is null or empty");
                        emitter.onError(new IllegalArgumentException("Message body template cannot be empty"));
                        return;
                    }

                    // Determine if the body is a Freemarker template or a direct JSON
                    String templateContent;
                    if (isValidFreemarkerTemplate(body)) {
                        templateContent = body;
                    } else {
                        // If it's a JSON, create a Freemarker template that renders the JSON
                        templateContent = "${body?json_string}";
                    }

                    // 2. Load the string as a Freemarker template
                    StringTemplateLoader loader = new StringTemplateLoader();
                    loader.putTemplate("userTemplate", templateContent);
                    cfg.setTemplateLoader(loader);

                    // 3. Prepare data model (you can add more fields here)
                    Map<String, Object> model = new HashMap<>();

                    model.put("body", body);

                    // Inject headers as a nested object: model.headers.X-User-Id
                    Map<String, String> headersMap = new HashMap<>();
                    ctx
                        .request()
                        .headers()
                        .names()
                        .forEach(name -> headersMap.put(name, ctx.request().headers().get(name)));
                    model.put("headers", headersMap);

                    // Inject query params
                    Map<String, String> queryMap = new HashMap<>();
                    ctx
                        .request()
                        .parameters()
                        .keySet()
                        .forEach(name -> queryMap.put(name, ctx.request().parameters().getFirst(name)));
                    model.put("query", queryMap);

                    // Add custom data
                    // model.put("subscriptionId", subscriptionId);
                    // model.put("timestamp", System.currentTimeMillis());

                    // 4. Render the template
                    Template template = cfg.getTemplate("userTemplate");
                    StringWriter writer = new StringWriter();
                    template.process(model, writer);
                    String renderedMessage = writer.toString();

                    channel.basicPublish(
                        "", // default exchange
                        subscriptionId, // routing key = queue name
                        null, // default properties
                        renderedMessage.getBytes(StandardCharsets.UTF_8)
                    );

                    log.info("Message published to queue: {}", subscriptionId);

                    // Clean up
                    channel.close();
                    connection.close();
                    emitter.onComplete();
                }
            } catch (Exception e) {
                emitter.onError(e);
            }
        });
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        return Completable.complete();
    }

    // Simple method to check if the body looks like a Freemarker template
    private boolean isValidFreemarkerTemplate(String content) {
        return content != null && (content.contains("${") || content.contains("<#"));
    }
}
