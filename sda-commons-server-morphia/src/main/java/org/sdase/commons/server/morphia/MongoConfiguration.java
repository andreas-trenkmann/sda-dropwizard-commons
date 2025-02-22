package org.sdase.commons.server.morphia;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mongodb.MongoClientURI;
import javax.validation.constraints.AssertTrue;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MongoConfiguration.class);

  /**
   * Comma separated list of hosts with their port that build the MongoDB cluster:
   *
   * <pre>{@code mongo-db-a:27018,mongo-db-b:27018,mongo-db-c:27018}</pre>
   *
   * <p>The default port if no port is specified is {@code :27017}:
   *
   * <p>{@code mongo-db-a,mongo-db-b,mongo-db-c} is equal to {@code
   * mongo-db-a:27017,mongo-db-b:27017,mongo-db-c:27017}
   *
   * <p>Details in the <a
   * href="https://docs.mongodb.com/manual/reference/connection-string/">connection string
   * documentation</a> for host1 to hostN.
   */
  private String hosts;

  /**
   * The name of the mongo database to access.
   *
   * <p>Details in the <a
   * href="https://docs.mongodb.com/manual/reference/connection-string/">connection string
   * documentation</a> for database.
   */
  private String database;

  /**
   * Additional options for the connection.
   *
   * <p>Details in the <a
   * href="https://docs.mongodb.com/manual/reference/connection-string/">connection string
   * documentation</a> for options.
   */
  private String options = "";

  /**
   * The username used for login at the MongoDB.
   *
   * <p>Details in the <a
   * href="https://docs.mongodb.com/manual/reference/connection-string/">connection string
   * documentation</a> for username:password.
   */
  private String username;

  /**
   * The password used for login at the MongoDB.
   *
   * <p>Details in the <a
   * href="https://docs.mongodb.com/manual/reference/connection-string/">connection string
   * documentation</a> for username:password.
   */
  private String password;

  /** If SSL should be used for the database connection. */
  private boolean useSsl;

  /**
   * The content of a CA certificate (list) in PEM format. This certificates are added to the {@link
   * javax.net.ssl.TrustManager}s to verify the connection. The string represents the content of a
   * regular PEM file, e.g.:
   *
   * <pre>
   * -----BEGIN CERTIFICATE-----
   * MIIEkjCCA3qgAwIBAgIQCgFBQgAAAVOFc2oLheynCDANBgkqhkiG9w0BAQsFADA/
   * MSQwIgYDVQQKExtEaWdpdGFsIFNpZ25hdHVyZSBUcnVzdCBDby4xFzAVBgNVBAMT
   * ...
   * X4Po1QYz+3dszkDqMp4fklxBwXRsW10KXzPMTZ+sOPAveyxindmjkW8lGy+QsRlG
   * PfZ+G6Z6h7mjem0Y+iWlkYcV4PIWL1iwBi8saCbGS5jN2p8M+X+Q7UNKEkROb3N6
   * KOqkqm57TH2H3eDJAkSnh6/DNFu0Qg==
   * -----END CERTIFICATE-----
   * </pre>
   *
   * @deprecated Instead of using CA Certificate as a string from the environment, it is preferred
   *     to mount CA certificates directly in {@value
   *     org.sdase.commons.shared.certificates.ca.CaCertificatesBundle#DEFAULT_TRUSTED_CERTIFICATES_DIR}.
   *     When providing a {@link
   *     org.sdase.commons.shared.certificates.ca.CaCertificateConfiguration} the directory can be
   *     configured.
   */
  @Deprecated private String caCertificate;

  /**
   * Connection string that includes all necessary information to establish a connection with the
   * MongoDB cluster.
   *
   * @see <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">Official
   *     Documentation</a>
   */
  private String connectionString;

  public String getHosts() {
    if (StringUtils.isBlank(hosts) && StringUtils.isNotBlank(connectionString)) {
      return String.join(",", new MongoClientURI(connectionString).getHosts());
    }
    return hosts;
  }

  public MongoConfiguration setHosts(String hosts) {
    this.hosts = hosts;
    return this;
  }

  public String getDatabase() {
    if (StringUtils.isBlank(database) && StringUtils.isNotBlank(connectionString)) {
      return new MongoClientURI(connectionString).getDatabase();
    }
    return database;
  }

  public MongoConfiguration setDatabase(String database) {
    this.database = database;
    return this;
  }

  public String getOptions() {
    if (StringUtils.isBlank(options) && StringUtils.isNotBlank(connectionString)) {
      // TODO what should be returned?
    }
    return options;
  }

  public MongoConfiguration setOptions(String options) {
    this.options = options;
    return this;
  }

  public String getUsername() {
    if (StringUtils.isBlank(username) && StringUtils.isNotBlank(connectionString)) {
      return new MongoClientURI(connectionString).getUsername();
    }
    return username;
  }

  public MongoConfiguration setUsername(String username) {
    this.username = username;
    return this;
  }

  public String getPassword() {
    if (StringUtils.isBlank(password) && StringUtils.isNotBlank(connectionString)) {
      return new String(new MongoClientURI(connectionString).getPassword());
    }
    return password;
  }

  public MongoConfiguration setPassword(String password) {
    this.password = password;
    return this;
  }

  public boolean isUseSsl() {
    return useSsl;
  }

  public MongoConfiguration setUseSsl(boolean useSsl) {
    this.useSsl = useSsl;
    return this;
  }

  /**
   * @return The content of a CA certificate (list) in PEM format. This certificates are added to
   *     the {@link javax.net.ssl.TrustManager}s to verify the connection. The string represents the
   *     content of a regular PEM file.
   * @deprecated Instead of using CA Certificate as a string from the environment, it is preferred
   *     to mount CA certificates directly in {@value
   *     org.sdase.commons.shared.certificates.ca.CaCertificatesBundle#DEFAULT_TRUSTED_CERTIFICATES_DIR}.
   *     When providing a {@link
   *     org.sdase.commons.shared.certificates.ca.CaCertificateConfiguration} the directory can be
   *     configured.
   */
  @Deprecated
  public String getCaCertificate() {
    return caCertificate;
  }

  /**
   * @param caCertificate The content of a CA certificate (list) in PEM format. This certificates
   *     are added to the {@link javax.net.ssl.TrustManager}s to verify the connection. The string
   *     represents the content of a regular PEM file.
   * @return this instance
   * @deprecated Instead of using CA Certificate as a string from the environment, it is preferred
   *     to mount CA certificates directly in {@value
   *     org.sdase.commons.shared.certificates.ca.CaCertificatesBundle#DEFAULT_TRUSTED_CERTIFICATES_DIR}.
   *     When providing a {@link
   *     org.sdase.commons.shared.certificates.ca.CaCertificateConfiguration} the directory can be
   *     configured.
   */
  @Deprecated
  public MongoConfiguration setCaCertificate(String caCertificate) {
    this.caCertificate = caCertificate;
    return this;
  }

  public String getConnectionString() {
    return connectionString;
  }

  public MongoConfiguration setConnectionString(String connectionString) {
    this.connectionString = connectionString;
    return this;
  }

  /**
   * We either need the
   *
   * <ul>
   *   <li>'connectionString'
   *   <li>'hosts' and 'database'
   * </ul>
   *
   * @return true if the configuration is valid
   */
  @AssertTrue
  @JsonIgnore
  public boolean isValid() {
    if (StringUtils.isNotBlank(connectionString)) {
      return true;
    }
    if (StringUtils.isBlank(hosts) || StringUtils.isBlank(database)) {
      LOGGER.error("Please specify either 'connectionString' or 'hosts' and 'database'");
      return false;
    }

    return true;
  }
}
