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
package io.gravitee.policy.rabbitmqconsumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.policy.rabbitmqconsumer.configuration.RabbitMQConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RabbitMQConsumerPolicy implements Policy {

    private final RabbitMQConfiguration configuration;
    private final ConnectionFactory factory;
    private Integer timeOut;

    public RabbitMQConsumerPolicy(RabbitMQConfiguration configuration) {
        this.configuration = configuration;
        this.factory = new ConnectionFactory();
        factory.setHost(configuration.getHost());
        factory.setPort(configuration.getPort());
        factory.setUsername(configuration.getUsername());
        factory.setPassword(configuration.getPassword());
        factory.setVirtualHost("/");
        factory.setConnectionTimeout(5000);
        this.timeOut = configuration.getTimeout();
    }

    @Override
    public String id() {
        return "rabbitmq-consumer-policy";
    }

    @Override
    public Completable onRequest(HttpExecutionContext ctx) {
        return Completable.complete();
    }

    @Override
    public Completable onResponse(HttpExecutionContext ctx) {
        return Single
            .fromCallable(() -> {
                String subscriptionId = ctx.getAttribute("subscription-id");
                if (subscriptionId == null) {
                    throw new IllegalArgumentException("Subscription ID not found in context");
                }

                final Connection connection = factory.newConnection();
                final Channel channel = connection.createChannel();

                try {
                    // Try to check if queue exists first
                    try {
                        channel.queueDeclarePassive(subscriptionId);
                    } catch (IOException e) {
                        // Queue doesn't exist, declare it
                        channel.queueDeclare(
                            subscriptionId,
                            false, // durable
                            false, // exclusive
                            true, // autoDelete
                            Map.of("x-expires", this.timeOut)
                        );
                    }

                    // Consume single message
                    GetResponse response = channel.basicGet(subscriptionId, true);
                    if (response == null) {
                        throw new TimeoutException("No message available in queue");
                    }

                    return new String(response.getBody(), "UTF-8");
                } finally {
                    // Always close resources
                    closeResources(connection, channel);
                }
            })
            .flatMapCompletable(message ->
                Completable.fromAction(() -> {
                    ctx.response().headers().set("Content-Type", "text/plain");
                    ctx.response().body(Buffer.buffer(message));
                    ctx.response().end(ctx);
                })
            )
            .onErrorResumeNext(error ->
                ctx.interruptWith(
                    new ExecutionFailure(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                        .message(
                            "RabbitMQ message retrieval failed: " +
                            (error.getMessage() != null ? error.getMessage() : "Unknown error")
                        )
                )
            );
    }

    private void closeResources(final Connection connection, final Channel channel) {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            log.error("Error closing RabbitMQ resources", e);
        }
    }
}
