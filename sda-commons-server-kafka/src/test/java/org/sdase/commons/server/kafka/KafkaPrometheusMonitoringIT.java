package org.sdase.commons.server.kafka;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.salesforce.kafka.test.junit4.SharedKafkaTestResource;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.sdase.commons.server.kafka.builder.MessageListenerRegistration;
import org.sdase.commons.server.kafka.builder.ProducerRegistration;
import org.sdase.commons.server.kafka.consumer.IgnoreAndProceedErrorHandler;
import org.sdase.commons.server.kafka.consumer.strategies.autocommit.AutocommitMLS;
import org.sdase.commons.server.kafka.dropwizard.KafkaTestApplication;
import org.sdase.commons.server.kafka.dropwizard.KafkaTestConfiguration;
import org.sdase.commons.server.kafka.producer.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaPrometheusMonitoringIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPrometheusMonitoringIT.class);

  private static final SharedKafkaTestResource KAFKA =
      new SharedKafkaTestResource()
          // we only need one consumer offsets partition
          .withBrokerProperty("offsets.topic.num.partitions", "1")
          // we don't need to wait that a consumer group rebalances since we always start with a
          // fresh kafka instance
          .withBrokerProperty("group.initial.rebalance.delay.ms", "0");

  private static final String CONSUMER_1 = "consumer1";

  private static final String PRODUCER_1 = "producer1";

  private static final DropwizardAppRule<KafkaTestConfiguration> DROPWIZARD_APP_RULE =
      new DropwizardAppRule<>(
          KafkaTestApplication.class,
          resourceFilePath("test-config-default.yml"),
          config("kafka.brokers", KAFKA::getKafkaConnectString),

          // performance improvements in the tests
          config("kafka.config.heartbeat\\.interval\\.ms", "250"));

  @ClassRule
  public static final TestRule CHAIN = RuleChain.outerRule(KAFKA).around(DROPWIZARD_APP_RULE);

  private List<Long> resultsLong = Collections.synchronizedList(new ArrayList<>());

  private KafkaBundle<KafkaTestConfiguration> kafkaBundle;

  @Before
  public void before() {
    KafkaTestApplication app = DROPWIZARD_APP_RULE.getApplication();
    kafkaBundle = app.kafkaBundle();
    resultsLong.clear();
  }

  @Test
  public void shouldWriteHelpAndTypeToMetrics() {
    String topic = "testConsumeMsgForPrometheus";
    KAFKA.getKafkaTestUtils().createTopic(topic, 1, (short) 1);

    AutocommitMLS<Long, Long> longLongAutocommitMLS =
        new AutocommitMLS<>(
            record -> resultsLong.add(record.value()), new IgnoreAndProceedErrorHandler<>());

    kafkaBundle.createMessageListener(
        MessageListenerRegistration.builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(CONSUMER_1)
            .withListenerStrategy(longLongAutocommitMLS)
            .build());

    MessageProducer<Long, Long> producer =
        kafkaBundle.registerProducer(
            ProducerRegistration.<Long, Long>builder()
                .forTopic(topic)
                .withProducerConfig(PRODUCER_1)
                .build());

    // pass in messages
    producer.send(1L, 1L);
    producer.send(2L, 2L);

    await()
        .atMost(KafkaBundleConsts.N_MAX_WAIT_MS, MILLISECONDS)
        .until(() -> resultsLong.size() == 2);

    List<MetricFamilySamples> list =
        Collections.list(CollectorRegistry.defaultRegistry.metricFamilySamples());

    String[] metrics = {
      "kafka_producer_topic_message",
      "kafka_consumer_topic_message_duration",
      "kafka_consumer_records_lag"
    };

    assertThat(list).extracting(m -> m.name).contains(metrics);

    list.forEach(
        mfs -> {
          assertThat(mfs.samples.size()).isPositive();
          for (Collector.MetricFamilySamples.Sample sample : mfs.samples) {
            LOGGER.info(
                "Sample: name={}, value={}, labelNames={}, labelValues={}",
                sample.name,
                sample.value,
                sample.labelNames,
                sample.labelValues);
          }
        });

    assertThat(
            CollectorRegistry.defaultRegistry.getSampleValue(
                "kafka_producer_topic_message_total",
                new String[] {"producer_name", "topic_name"},
                new String[] {PRODUCER_1, topic}))
        .as("sample value for metric 'kafka_producer_topic_message_total'")
        .isEqualTo(2);

    assertThat(
            CollectorRegistry.defaultRegistry.getSampleValue(
                "kafka_consumer_topic_message_duration_count",
                new String[] {"consumer_name", "topic_name"},
                new String[] {CONSUMER_1 + "-0", topic}))
        .as("sample value for metric 'kafka_consumer_topic_message_duration_count'")
        .isEqualTo(2);
  }
}
