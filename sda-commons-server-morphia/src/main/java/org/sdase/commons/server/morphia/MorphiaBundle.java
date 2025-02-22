package org.sdase.commons.server.morphia;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.mongodb.MongoClient;
import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.ValidationExtension;
import dev.morphia.converters.LocalDateTimeConverter;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.sdase.commons.server.morphia.converter.LocalDateConverter;
import org.sdase.commons.server.morphia.converter.ZonedDateTimeConverter;
import org.sdase.commons.server.morphia.health.MongoHealthCheck;
import org.sdase.commons.server.morphia.internal.MongoClientBuilder;
import org.sdase.commons.shared.certificates.ca.CaCertificateConfigurationProvider;
import org.sdase.commons.shared.certificates.ca.CaCertificatesBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A bundle used to create a connection to a MongoDB that is accessed through a <a
 * href="http://morphiaorg.github.io/morphia/">Morphia</a> {@link Datastore}.
 *
 * @param <C> The subtype of {@link Configuration}, the {@link io.dropwizard.Application} uses.
 */
public class MorphiaBundle<C extends Configuration> implements ConfiguredBundle<C> {

  private static final Logger LOGGER = LoggerFactory.getLogger(MorphiaBundle.class);

  /**
   * {@link LocalDateTimeConverter} is already included in Morphia's {@link
   * dev.morphia.converters.DefaultConverters}. We do not use {@link
   * dev.morphia.converters.LocalDateConverter} because it actually stores a date time.
   */
  private static final Set<TypeConverter> DEFAULT_CONVERTERS =
      new LinkedHashSet<>(asList(new ZonedDateTimeConverter(), new LocalDateConverter()));

  private final Function<C, MongoConfiguration> configurationProvider;
  private final Set<TypeConverter> customConverters = new LinkedHashSet<>();
  private final Set<String> packagesToScan = new HashSet<>();

  @SuppressWarnings("rawtypes") // same type as in Morphia
  private final Set<Class> entityClasses = new HashSet<>();

  private final boolean ensureIndexes;
  private final boolean forceEnsureIndexes;
  private final Tracer tracer;
  /** Activate JSR303 validation. */
  private final boolean activateValidation;

  private final CaCertificatesBundle.FinalBuilder<C> caCertificatesBundleBuilder;
  private MongoClient mongoClient;
  private Datastore morphiaDatastore;
  private SSLContext sslContext;
  private CaCertificatesBundle<C> caCertificatesBundle;

  private MorphiaBundle( // NOSONAR: Methods should not have too many parameters
      MongoConfigurationProvider<C> configProvider,
      Set<String> packagesToScan,
      @SuppressWarnings("rawtypes") // same type as in Morphia
          Set<Class> entityClasses,
      Set<TypeConverter> customConverters,
      boolean ensureIndexes,
      boolean forceEnsureIndexes,
      boolean activateValidation,
      Tracer tracer,
      CaCertificatesBundle.FinalBuilder<C> caCertificatesBundleBuilder) {
    this.configurationProvider = configProvider;
    this.tracer = tracer;
    this.packagesToScan.addAll(packagesToScan);
    this.entityClasses.addAll(entityClasses);
    this.customConverters.addAll(customConverters);
    this.ensureIndexes = ensureIndexes;
    this.forceEnsureIndexes = forceEnsureIndexes;
    this.activateValidation = activateValidation;
    this.caCertificatesBundleBuilder = caCertificatesBundleBuilder;
  }

  //
  // Builder
  //
  public static InitialBuilder builder() {
    return new Builder<>();
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    this.caCertificatesBundle = caCertificatesBundleBuilder.build();
    bootstrap.addBundle((ConfiguredBundle) this.caCertificatesBundle);
  }

  @Override
  public void run(C configuration, Environment environment) {
    MongoConfiguration mongoConfiguration = configurationProvider.apply(configuration);

    checkMongoConfig(mongoConfiguration);

    Morphia configuredMorphia = new Morphia();
    customConverters.forEach(cc -> configuredMorphia.getMapper().getConverters().addConverter(cc));
    if (!entityClasses.isEmpty()) {
      configuredMorphia.map(entityClasses);
    }
    this.packagesToScan.forEach(configuredMorphia::mapPackage);

    // get the sslContext instance produced by the caCertificateBundle
    this.sslContext = this.caCertificatesBundle.getSslContext();

    this.mongoClient = createClient(environment, mongoConfiguration);
    this.morphiaDatastore =
        configuredMorphia.createDatastore(mongoClient, mongoConfiguration.getDatabase());
    if (ensureIndexes) {
      new IndexEnsurer(this.datastore(), forceEnsureIndexes).ensureIndexes();
    }
    if (activateValidation) {
      new ValidationExtension(configuredMorphia);
    }

    registerHealthCheck(environment.healthChecks(), mongoConfiguration.getDatabase());
  }

  /**
   * registers a health check for the mongo database
   *
   * @param healthCheckRegistry registry where to register health checks
   * @param database database name that is used within health check
   */
  private void registerHealthCheck(HealthCheckRegistry healthCheckRegistry, String database) {
    healthCheckRegistry.register(
        "mongo", new MongoHealthCheck(mongoClient().getDatabase((database))));
  }

  /**
   * @return the {@link MongoClient} that is connected to the MongoDB cluster. The client may be
   *     used for raw MongoDB operations. Usually the Morphia {@linkplain #datastore() Datastore}
   *     should be preferred for database operations.
   * @throws IllegalStateException if the method is called before the mongoClient is initialized in
   *     {@link #run(Configuration, Environment)}
   */
  public MongoClient mongoClient() {
    if (mongoClient == null) {
      throw new IllegalStateException(
          "Could not access mongoClient before Application#run(Configuration, Environment).");
    }
    return mongoClient;
  }

  /**
   * @return the configured {@link Datastore} that is ready to access the MongoDB defined in {@link
   *     MongoConfiguration}.
   * @throws IllegalStateException if the method is called before the datastore is initialized in
   *     {@link #run(Configuration, Environment)}
   */
  public Datastore datastore() {
    if (morphiaDatastore == null) {
      throw new IllegalStateException(
          "Could not access datastore before Application#run(Configuration, Environment).");
    }
    return morphiaDatastore;
  }

  private MongoClient createClient(Environment environment, MongoConfiguration mongoConfiguration) {
    return new MongoClientBuilder(mongoConfiguration)
        .withSSlContext(sslContext)
        .withTracer(tracer)
        .build(environment);
  }

  public void checkMongoConfig(MongoConfiguration config) {
    final String caCertificate = config.getCaCertificate();
    if (StringUtils.isNotBlank(caCertificate)
        && caCertificate.startsWith("${")
        && caCertificate.endsWith("}")) {
      LOGGER.warn("Missing default value for caCertificate in config.yml");
    }
  }

  public interface InitialBuilder {

    /**
     * @param configurationProvider the method reference that provides the @{@link
     *     MongoConfiguration} from the applications configurations class
     * @param <C> the type of the applications configuration class
     * @return a builder instance for further configuration
     */
    <C extends Configuration> CaCertificateConfigProviderBuilder<C> withConfigurationProvider(
        @NotNull MongoConfigurationProvider<C> configurationProvider);
  }

  public interface ScanPackageBuilder<C extends Configuration> {

    /**
     * @param entityClass A model class that represents an entity. Using explicit classes instead of
     *     scanning packages boosts application startup.
     * @return a builder instance for further configuration
     */
    default DisableConverterBuilder<C> withEntity(Class<?> entityClass) {
      return withEntities(entityClass);
    }

    /**
     * @param entityClasses Model classes that represent entities. Using explicit classes instead of
     *     scanning packages boosts application startup.
     * @return a builder instance for further configuration
     */
    default DisableConverterBuilder<C> withEntities(Class<?>... entityClasses) {
      return withEntities(asList(entityClasses));
    }

    /**
     * @param entityClasses Model classes that represent entities. Using explicit classes instead of
     *     scanning packages boosts application startup.
     * @return a builder instance for further configuration
     */
    DisableConverterBuilder<C> withEntities(@NotNull Iterable<Class<?>> entityClasses);

    /**
     * @param packageToScanForEntities The package that should be scanned for entities recursively.
     * @return a builder instance for further configuration
     */
    DisableConverterBuilder<C> withEntityScanPackage(@NotNull String packageToScanForEntities);

    /**
     * @param markerClass A class or interface that defines the base package for recursive entity
     *     scanning. The class may be a marker interface or a specific entity class.
     * @return a builder instance for further configuration
     */
    default DisableConverterBuilder<C> withEntityScanPackageClass(Class<?> markerClass) {
      return withEntityScanPackage(markerClass.getPackage().getName());
    }
  }

  public interface DisableConverterBuilder<C extends Configuration>
      extends CustomConverterBuilder<C> {

    /**
     * Disables the default {@link TypeConverter}s defined in {@link
     * MorphiaBundle#DEFAULT_CONVERTERS}.
     *
     * @return a builder instance for further configuration
     */
    CustomConverterBuilder<C> disableDefaultTypeConverters();
  }

  public interface CustomConverterBuilder<C extends Configuration>
      extends EnsureIndexesConfigBuilder<C> {

    /**
     * Adds a custom {@link TypeConverter}s, see {@link
     * dev.morphia.converters.Converters#addConverter(TypeConverter)}
     *
     * @param typeConverters the converters to add
     * @return a builder instance for further configuration
     */
    CustomConverterBuilder<C> addCustomTypeConverters(Iterable<TypeConverter> typeConverters);

    /**
     * Adds a custom {@link TypeConverter}s, see {@link
     * dev.morphia.converters.Converters#addConverter(TypeConverter)}
     *
     * @param typeConverters the converters to add
     * @return a builder instance for further configuration
     */
    default CustomConverterBuilder<C> addCustomTypeConverters(TypeConverter... typeConverters) {
      return addCustomTypeConverters(asList(typeConverters));
    }

    /**
     * Adds a custom {@link TypeConverter}, see {@link
     * dev.morphia.converters.Converters#addConverter(TypeConverter)}
     *
     * @param typeConverter the converter to add
     * @return a builder instance for further configuration
     */
    default CustomConverterBuilder<C> addCustomTypeConverter(TypeConverter typeConverter) {
      return addCustomTypeConverters(singletonList(typeConverter));
    }
  }

  public interface EnsureIndexesConfigBuilder<C extends Configuration> extends FinalBuilder<C> {

    /**
     * Executes {@link Datastore#ensureIndexes()} after connecting to let Morphia create all
     * annotated indexes. This operation may fail and stop the application if existing indexes are
     * modified.
     *
     * <p><strong>This is the default option.</strong>
     *
     * @return a builder instance for further configuration
     */
    FinalBuilder<C> ensureIndexes();

    /**
     * Disables the default behaviour that indexes for entity classes are created on startup. One
     * has to call {@link Datastore#ensureIndexes()} manually to create indexes for the entity
     * collections. This may be necessary if existing indexes are modified and need to be dropped
     * before recreating them.
     *
     * @return a builder instance for further configuration
     */
    FinalBuilder<C> skipEnsureIndexes();

    /**
     * Executes {@link Datastore#ensureIndexes()} after connecting to let Morphia create all
     * annotated indexes. If existing indexes are modified all indexes are dropped and recreated.
     *
     * <p><strong>Due to limitations of MongoDB, it is not possible to rename an index. If an index
     * must be renamed, a custom migration must be implemented to drop the index before creating it
     * with a new name. The {@link IndexEnsurer} may be used for such a custom migration.</strong>
     *
     * @return a builder instance for further configuration
     */
    FinalBuilder<C> forceEnsureIndexes();
  }

  public interface CaCertificateConfigProviderBuilder<C extends Configuration>
      extends ScanPackageBuilder<C> {
    /**
     * * Add the ability to use SSl for connection with the database. If no specific configuration
     * is provided it will try to look for the pem files in the default directory {@value
     * org.sdase.commons.shared.certificates.ca.CaCertificatesBundle#DEFAULT_TRUSTED_CERTIFICATES_DIR}
     *
     * @param configProvider
     * @return a builder instance for further configuration
     */
    FinalBuilder<C> withCaCertificateConfigProvider(
        CaCertificateConfigurationProvider<C> configProvider);
  }

  public interface FinalBuilder<C extends Configuration> extends ScanPackageBuilder<C> {

    /**
     * Enable JSR303 validation for entities.
     *
     * @return a builder instance for further configuration
     */
    FinalBuilder<C> withValidation();

    /**
     * Disable JSR303 validation for entities.
     *
     * @return a builder instance for further configuration
     */
    FinalBuilder<C> withoutValidation();

    /**
     * Specifies a custom tracer to use. If no tracer is specified, the {@link GlobalTracer} is
     * used.
     *
     * @param tracer The tracer to use
     * @return the same builder
     */
    FinalBuilder<C> withTracer(Tracer tracer);

    /**
     * Builds the mongo bundle
     *
     * @return mongo bundle
     */
    MorphiaBundle<C> build();
  }

  public static class Builder<T extends Configuration>
      implements InitialBuilder,
          ScanPackageBuilder<T>,
          DisableConverterBuilder<T>,
          CustomConverterBuilder<T>,
          EnsureIndexesConfigBuilder<T>,
          CaCertificateConfigProviderBuilder<T>,
          FinalBuilder<T> {

    private final MongoConfigurationProvider<T> configProvider;
    private final Set<TypeConverter> customConverters = new LinkedHashSet<>(DEFAULT_CONVERTERS);
    private final Set<String> packagesToScan = new HashSet<>();

    @SuppressWarnings("rawtypes") // same type as in Morphia
    private final Set<Class> entityClasses = new HashSet<>();

    private CaCertificatesBundle.FinalBuilder<T> caCertificatesBundleBuilder =
        CaCertificatesBundle.builder();
    private boolean ensureIndexes = true;
    private boolean forceEnsureIndexes = false;
    private boolean activateValidation = false;
    private Tracer tracer;

    private Builder() {
      configProvider = null;
    }

    private Builder(MongoConfigurationProvider<T> configProvider) {
      this.configProvider = configProvider;
    }

    @Override
    public <C extends Configuration>
        CaCertificateConfigProviderBuilder<C> withConfigurationProvider(
            MongoConfigurationProvider<C> configurationProvider) {
      return new Builder<>(configurationProvider);
    }

    @Override
    public DisableConverterBuilder<T> withEntities(Iterable<Class<?>> entityClasses) {
      entityClasses.forEach(this.entityClasses::add);
      return this;
    }

    @Override
    public DisableConverterBuilder<T> withEntityScanPackage(String packageToScanForEntities) {
      packagesToScan.add(Validate.notBlank(packageToScanForEntities));
      return this;
    }

    @Override
    public CustomConverterBuilder<T> disableDefaultTypeConverters() {
      this.customConverters.clear();
      return this;
    }

    @Override
    public CustomConverterBuilder<T> addCustomTypeConverters(
        Iterable<TypeConverter> typeConverter) {
      typeConverter.forEach(customConverters::add);
      return this;
    }

    @Override
    public FinalBuilder<T> ensureIndexes() {
      this.ensureIndexes = true;
      this.forceEnsureIndexes = false;
      return this;
    }

    @Override
    public FinalBuilder<T> skipEnsureIndexes() {
      this.ensureIndexes = false;
      this.forceEnsureIndexes = false;
      return this;
    }

    @Override
    public FinalBuilder<T> forceEnsureIndexes() {
      this.ensureIndexes = true;
      this.forceEnsureIndexes = true;
      return this;
    }

    @Override
    public FinalBuilder<T> withValidation() {
      this.activateValidation = true;
      return this;
    }

    @Override
    public FinalBuilder<T> withoutValidation() {
      this.activateValidation = false;
      return this;
    }

    @Override
    public FinalBuilder<T> withCaCertificateConfigProvider(
        CaCertificateConfigurationProvider<T> configProvider) {
      this.caCertificatesBundleBuilder =
          CaCertificatesBundle.builder().withCaCertificateConfigProvider(configProvider);
      return this;
    }

    public FinalBuilder<T> withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    @Override
    public MorphiaBundle<T> build() {
      return new MorphiaBundle<>(
          configProvider,
          packagesToScan,
          entityClasses,
          customConverters,
          ensureIndexes,
          forceEnsureIndexes,
          activateValidation,
          tracer,
          caCertificatesBundleBuilder);
    }
  }
}
