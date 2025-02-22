package org.sdase.commons.server.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.sdase.commons.server.jackson.JacksonConfigurationBundle;
import org.sdase.commons.server.starter.test.BundleAssertion;

public class JacksonBuilderTest {

  private BundleAssertion<SdaPlatformConfiguration> bundleAssertion;

  @Before
  public void setUp() {
    bundleAssertion = new BundleAssertion<>();
  }

  @Test
  public void defaultJacksonConfig() {
    SdaPlatformBundle<SdaPlatformConfiguration> bundle =
        SdaPlatformBundle.builder()
            .usingSdaPlatformConfiguration()
            .withoutConsumerTokenSupport()
            .withSwaggerInfoTitle("Starter") // NOSONAR
            .addSwaggerResourcePackageClass(this.getClass())
            .build();

    bundleAssertion.assertBundleConfiguredByPlatformBundle(
        bundle, JacksonConfigurationBundle.builder().build());
  }

  @Test
  public void noHalSupport() {
    SdaPlatformBundle<SdaPlatformConfiguration> bundle =
        SdaPlatformBundle.builder()
            .usingSdaPlatformConfiguration()
            .withoutConsumerTokenSupport()
            .withSwaggerInfoTitle("Starter")
            .addSwaggerResourcePackageClass(this.getClass())
            .withoutHalSupport()
            .build();

    bundleAssertion.assertBundleConfiguredByPlatformBundle(
        bundle, JacksonConfigurationBundle.builder().withoutHalSupport().build());
  }

  @Test
  public void noFieldFilter() {
    SdaPlatformBundle<SdaPlatformConfiguration> bundle =
        SdaPlatformBundle.builder()
            .usingSdaPlatformConfiguration()
            .withoutConsumerTokenSupport()
            .withSwaggerInfoTitle("Starter")
            .addSwaggerResourcePackageClass(this.getClass())
            .withoutFieldFilter()
            .build();

    bundleAssertion.assertBundleConfiguredByPlatformBundle(
        bundle, JacksonConfigurationBundle.builder().withoutFieldFilter().build());
  }

  @Test
  public void alwaysWithMillis() throws JsonProcessingException {
    SdaPlatformBundle<SdaPlatformConfiguration> bundle =
        SdaPlatformBundle.builder()
            .usingSdaPlatformConfiguration()
            .withoutConsumerTokenSupport()
            .withSwaggerInfoTitle("Starter")
            .addSwaggerResourcePackageClass(this.getClass())
            .alwaysWriteZonedDateTimeWithMillisInJson()
            .build();

    ObjectMapper om = bundleAssertion.getObjectMapper(bundle);
    FooWithZonedDateTime fooObject = new FooWithZonedDateTime();
    assertThat(fooObject.getNow().getNano()).isNotZero();
    String fooString = om.writeValueAsString(fooObject);

    FooWithZonedDateTime deserializedFoo = om.readValue(fooString, FooWithZonedDateTime.class);
    assertThat(deserializedFoo.getNow().getNano()).isNotZero();
    assertThat(deserializedFoo.getNow())
        .isCloseTo(fooObject.getNow(), within(1, ChronoUnit.MILLIS));
  }

  @Test
  public void alwaysWithoutMillis() throws JsonProcessingException {
    SdaPlatformBundle<SdaPlatformConfiguration> bundle =
        SdaPlatformBundle.builder()
            .usingSdaPlatformConfiguration()
            .withoutConsumerTokenSupport()
            .withSwaggerInfoTitle("Starter")
            .addSwaggerResourcePackageClass(this.getClass())
            .alwaysWriteZonedDateTimeWithoutMillisInJson()
            .build();

    ObjectMapper om = bundleAssertion.getObjectMapper(bundle);
    FooWithZonedDateTime fooObject = new FooWithZonedDateTime();
    assertThat(fooObject.getNow().getNano()).isNotZero();
    String fooString = om.writeValueAsString(fooObject);

    FooWithZonedDateTime deserializedFoo = om.readValue(fooString, FooWithZonedDateTime.class);
    assertThat(deserializedFoo.getNow().getNano()).isZero();
    assertThat(deserializedFoo.getNow())
        .isCloseTo(fooObject.getNow(), within(1, ChronoUnit.SECONDS));
  }

  @Test
  public void withCustomizer() {

    Consumer<ObjectMapper> omc =
        om -> om.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);

    SdaPlatformBundle<SdaPlatformConfiguration> bundle =
        SdaPlatformBundle.builder()
            .usingSdaPlatformConfiguration()
            .withoutConsumerTokenSupport()
            .withSwaggerInfoTitle("Starter")
            .addSwaggerResourcePackageClass(this.getClass())
            .withObjectMapperCustomization(omc)
            .build();

    ObjectMapper om = bundleAssertion.getObjectMapper(bundle);
    assertThat(om.isEnabled(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)).isFalse();
  }

  static class FooWithZonedDateTime {

    private ZonedDateTime now = ZonedDateTime.now();

    public ZonedDateTime getNow() {
      return now;
    }

    public FooWithZonedDateTime setNow(ZonedDateTime now) {
      this.now = now;
      return this;
    }
  }
}
