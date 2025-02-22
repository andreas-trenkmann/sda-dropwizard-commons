package org.sdase.commons.server.weld.testing.junit4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sdase.commons.server.testing.DropwizardConfigurationHelper.configFrom;

import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.dropwizard.util.JarLocation;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.ClassRule;
import org.junit.Test;
import org.sdase.commons.server.weld.testing.WeldAppRule;
import org.sdase.commons.server.weld.testing.test.AppConfiguration;
import org.sdase.commons.server.weld.testing.test.WeldExampleApplication;

/** @deprecated see {@link WeldAppRule} */
@Deprecated
public class WeldBundleProgrammaticConfigTest {

  private static final String LOCALHOST = "http://localhost:";

  @ClassRule
  public static final DropwizardAppRule<AppConfiguration> RULE =
      new WeldAppRule<>(
          WeldExampleApplication.class,
          configFrom(AppConfiguration::new).withPorts(4567, 0).withRootPath("/api/*").build());

  @Test
  public void testResource() {
    String response =
        RULE.client().target(LOCALHOST + 4567 + "/api/dummy").request().get(String.class);
    assertThat(response).isNotNull().isEqualTo("hello foo");
  }

  @Test
  public void testServlet() {
    Response response =
        RULE.client().target(LOCALHOST + RULE.getLocalPort() + "/foo").request().get();
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void testCommand() {
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
    WeldExampleApplication app = RULE.getApplication();

    final JarLocation location = mock(JarLocation.class);
    final Bootstrap<AppConfiguration> bootstrap = new Bootstrap<>(RULE.getApplication());
    when(location.toString()).thenReturn("dw-thing.jar");
    when(location.getVersion()).thenReturn(Optional.of("1.0.0"));
    bootstrap.addCommand(app.getTestCommand());

    Cli cli = new Cli(location, bootstrap, stdOut, stdErr);

    assertThat(cli.run("testDW")).isEmpty();

    assertThat(app.getTestCommand().getResult()).isEqualTo("foo");
  }

  @Test
  public void testTask() {
    WeldExampleApplication app = RULE.getApplication();

    try (Response response =
        RULE.client()
            .target(LOCALHOST + RULE.getAdminPort() + "/tasks/runTestTask")
            .request()
            .post(Entity.entity("", MediaType.TEXT_PLAIN))) {
      assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
      assertThat(app.getTestJobResult()).isEqualTo("foo");
    }
  }
}
