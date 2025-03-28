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
package io.gravitee.policy.rabbitmqpublisher.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.policy.api.PolicyConfiguration;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RabbitMQConfiguration implements PolicyConfiguration {

    @JsonProperty("AttributeQueueID")
    private String attributeQueueID = "subscription-id";

    @JsonProperty("CreateQueue")
    private Boolean createQueue = true;

    @JsonProperty("ConsumeQueue")
    private Boolean consumeQueue = true;

    @JsonProperty("RabbitMQHost")
    private String host = "localhost";

    @JsonProperty("RabbitMQPort")
    private Integer port = 5672;

    @JsonProperty("RabbitMQUsername")
    private String username;

    @JsonProperty("RabbitMQPassword")
    private String password;

    @JsonProperty("RabbitMQTimeout")
    private Integer timeout = 100000;

    @JsonProperty("RabbitMQQueueDurable")
    private Boolean queueDurable = false;

    @JsonProperty("RabbitMQQueueExclusive")
    private Boolean queueExclusive = false;

    @JsonProperty("RabbitMQQueueAutoDelete")
    private Boolean queueAutoDelete = true;

    @JsonProperty("Body")
    private String body =
        "{\n" +
        "  \\\"properties\\\": {},\n" +
        "  \\\"routing_key\\\": \\\"my_auto_delete_queue\\\",\n" +
        "  \\\"payload\\\": \\\"OK ban i\\\",\n" +
        "  \\\"payload_encoding\\\": \\\"string\\\"\n" +
        "}";
    // @JsonProperty("Scope")
    // private String scope = PolicyScope.REQUEST;
}
