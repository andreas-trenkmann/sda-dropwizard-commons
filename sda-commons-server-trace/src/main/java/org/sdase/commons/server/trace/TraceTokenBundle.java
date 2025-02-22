package org.sdase.commons.server.trace;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.sdase.commons.server.trace.filter.TraceTokenServerFilter;

public class TraceTokenBundle implements ConfiguredBundle<Configuration> {

  private TraceTokenBundle() {}

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    // nothing to initialize
  }

  @Override
  public void run(Configuration configuration, Environment environment) {
    TraceTokenServerFilter filter = new TraceTokenServerFilter();
    environment.jersey().register(filter);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    public TraceTokenBundle build() {
      return new TraceTokenBundle();
    }
  }
}
