# SDA Commons Server S3

[![javadoc](https://javadoc.io/badge2/org.sdase.commons/sda-commons-server-s3/javadoc.svg)](https://javadoc.io/doc/org.sdase.commons/sda-commons-server-s3)

This module provides the [`S3Bundle`](src/main/java/org/sdase/commons/server/s3/S3Bundle.java), 
a Dropwizard bundle that is used to perform operations on an object storage.

The bundle provides an S3 client based on the [Amazon AWS SDK](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/examples-s3.html).

## Usage

The [`S3Bundle`](src/main/java/org/sdase/commons/server/s3/S3Bundle.java) should be added as a
field in the application class instead of being anonymously added in the initialize method like other bundles of this 
library. Implementations need to refer to the instance to access the client.

The Dropwizard applications configuration class needs to provide a 
[`S3Configuration`](./src/main/java/org/sdase/commons/server/s3/S3Configuration.java).

The bundle builder requires to define the getter of the `S3Configuration` as method reference to access the 
configuration.

Afterwards `getClient()` is used to access an instance of `AmazonS3` that is used to operate on the 
object storage. 
See [`S3BundleTest`](./src/test/java/org/sdase/commons/server/s3/S3BundleTest.java) for a detailed usage example.   

### Tracing

The bundle comes with [OpenTracing](https://opentracing.io/) instrumentation.

### Health Check

The bundle supports the creation of both internal and external health checks.

```
private final S3Bundle<Config> s3Bundle =
    S3Bundle.builder()
        .withConfigurationProvider(Config::getS3Config)
        .withHealthCheck(Collections.singleton(Config::getS3Bucket))
        .build();
```

For creation of an external health check, use `.withExternalHealthCheck(Iterable<BucketNameProvider<C>>)`
respectively.