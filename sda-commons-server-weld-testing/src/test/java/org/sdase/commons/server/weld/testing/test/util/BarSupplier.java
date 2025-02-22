package org.sdase.commons.server.weld.testing.test.util;

import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;

public class BarSupplier implements Supplier<String> {

  @Inject
  @Named("foo")
  private String foo;

  @Override
  public String get() {
    return foo;
  }
}
