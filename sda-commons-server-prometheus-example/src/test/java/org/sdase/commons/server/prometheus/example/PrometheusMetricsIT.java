package org.sdase.commons.server.prometheus.example;

import static io.dropwizard.testing.ConfigOverride.randomPorts;
import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.core.Response;
import org.junit.ClassRule;
import org.junit.Test;
import org.sdase.commons.starter.SdaPlatformConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusMetricsIT {

  private static final Logger LOG = LoggerFactory.getLogger(PrometheusMetricsIT.class);

  @ClassRule
  public static final DropwizardAppRule<SdaPlatformConfiguration> DW =
      new DropwizardAppRule<>(
          MetricExampleApp.class,
          null,
          // use random ports so that tests can run in parallel
          // and do not affect each other when one is not shutting down
          randomPorts());

  @Test
  public void produceGaugeMetric() {

    String metrics = readMetrics();

    assertThat(metrics)
        .contains(
            "# HELP some_operation_temperature_celsius Tracks the temperature recorded within the operation.")
        .contains("# TYPE some_operation_temperature_celsius gauge");
  }

  @Test
  public void produceSuccessCounterMetric() {

    String metrics = readMetrics();

    assertThat(metrics)
        .contains(
            "# HELP some_operation_success_counter_total Counts successes occurred when some operation is invoked.")
        .contains("# TYPE some_operation_success_counter_total counter");
  }

  @Test
  public void produceErrorCounterMetric() {

    String metrics = readMetrics();

    assertThat(metrics)
        .contains(
            "# HELP some_operation_error_counter_total Counts errors occurred when some operation is invoked.")
        .contains("# TYPE some_operation_error_counter_total counter");
  }

  @Test
  public void produceHistogramMetric() {

    String metrics = readMetrics();

    assertThat(metrics)
        .contains(
            "# HELP some_operation_execution_duration_seconds Tracks duration of some operation.")
        .contains("# TYPE some_operation_execution_duration_seconds histogram");
  }

  private String readMetrics() {
    Response response =
        DW.client()
            .target(String.format("http://localhost:%d", DW.getAdminPort()) + "/metrics/prometheus")
            .request()
            .get();
    String metrics = response.readEntity(String.class);
    LOG.info("Prometheus metrics:\n{}", metrics);
    return metrics;
  }
}
