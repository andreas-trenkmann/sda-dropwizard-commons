package org.sdase.commons.server.opentelemetry;

import static io.dropwizard.testing.ConfigOverride.randomPorts;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.opentelemetry.api.GlobalOpenTelemetry;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;

@SetEnvironmentVariable(key = "OTEL_TRACES_EXPORTER", value = "logging")
@SetEnvironmentVariable(key = "MAIN_THREAD_CHECK_ENABLED", value = "false")
class AutoConfigurationTest {

  @RegisterExtension
  public static final DropwizardAppExtension<Configuration> DW =
      new DropwizardAppExtension<>(TraceTestApp.class, null, randomPorts());

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void shouldUseDefaults() {
    Response r = createClient().path("base/respond/test").request().get();

    r.readEntity(String.class);
    assertThat(r.getStatus()).isEqualTo(SC_OK);
    assertThat(GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().fields())
        .isNotEmpty()
        .contains("traceparent", "baggage", "uber-trace-id");
  }

  @Test
  @StdIo
  void shouldUseEnvironmentVariablesForConfiguration(StdOut out) {
    assertThat(System.getenv("OTEL_TRACES_EXPORTER")).isEqualTo("logging");

    Response r = createClient().path("base/respond/test").request().get();

    r.readEntity(String.class);

    assertThat(r.getStatus()).isEqualTo(SC_OK);

    // assert the logging exporter is used
    assertThat(out.capturedLines())
        .isNotEmpty()
        .anyMatch(
            l ->
                l.contains("[tracer: sda-commons-servlet:]")
                    && l.contains(
                        "io.opentelemetry.exporter.logging.LoggingSpanExporter: 'GET /base/respond/{value}'"));
  }

  private WebTarget createClient() {
    return DW.client().target("http://localhost:" + DW.getLocalPort());
  }

  public static class TraceTestApp extends Application<Configuration> {

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
      // use the autoConfigured module
      bootstrap.addBundle(
          OpenTelemetryBundle.builder().withAutoConfiguredTelemetryInstance().build());
    }

    @Override
    public void run(Configuration configuration, Environment environment) {
      environment.jersey().register(new TestApi());
    }
  }
}
