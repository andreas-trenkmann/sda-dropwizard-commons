package org.sdase.commons.client.jersey.token;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.sdase.commons.client.jersey.oidc.rest.IssuerClient;
import org.sdase.commons.client.jersey.oidc.rest.model.TokenResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads periodically new tokens and provides the last successfully loaded token. */
public class OidcService {

  private static final Logger log = LoggerFactory.getLogger(OidcService.class);

  private static final String BEARER = "Bearer";

  private static final Duration MAX_WAIT_TIME = Duration.ofSeconds(10L);

  private static final Duration MIN_DELAY_BETWEEN_LOADS = Duration.ofSeconds(5L);

  private static final Duration DELAY_BEFORE_RETRY_AFTER_FAILURE = Duration.ofSeconds(3L);

  private final IssuerClient tokenClient;

  private final Scheduler scheduler = new Scheduler(this::loadToken);

  private final AtomicReference<String> cachedToken = new AtomicReference<>();

  private final CountDownLatch ready = new CountDownLatch(1);

  public OidcService(IssuerClient client) {
    this.tokenClient = client;

    this.scheduler.start();
  }

  public void close() {
    this.scheduler.shutdown();
  }

  public String getAccessToken() {
    waitForToken();
    return this.cachedToken.get();
  }

  public String getBearerToken() {
    return String.format("%s %s", BEARER, this.getAccessToken());
  }

  /** Request token from JWT service and parse token from response. */
  private void loadToken() {
    Duration delayBeforeNextLoad = DELAY_BEFORE_RETRY_AFTER_FAILURE;
    try {
      delayBeforeNextLoad =
          Optional.ofNullable(this.tokenClient.getTokenResource())
              .filter(this::containsAccessToken)
              .map(this::updateCachedToken)
              .orElse(DELAY_BEFORE_RETRY_AFTER_FAILURE);
    } catch (RuntimeException e) {
      log.error("Error loading new token.", e);
    } finally {
      this.scheduler.schedule(delayBeforeNextLoad);
      log.debug("Next load scheduled in {}.", delayBeforeNextLoad);
    }
  }

  private boolean containsAccessToken(TokenResource token) {
    return token != null && token.getAccessToken() != null && !token.getAccessToken().isEmpty();
  }

  /**
   * Update token.
   *
   * @param token the new token
   * @return delay until we need to load the next token
   */
  private Duration updateCachedToken(TokenResource token) {
    this.cachedToken.set(token.getAccessToken());
    this.ready.countDown();
    final long nextDelaySeconds = Math.max(token.getAccessTokenExpiresInSeconds() / 3, 1);
    return nextDelaySeconds >= toSeconds(MIN_DELAY_BETWEEN_LOADS)
        ? Duration.ofSeconds(nextDelaySeconds)
        : MIN_DELAY_BETWEEN_LOADS;
  }

  /** Wait until the first token has been loaded. */
  private void waitForToken() {
    try {
      if (!this.ready.await(toSeconds(MAX_WAIT_TIME), TimeUnit.SECONDS)) {
        log.error("Could not load token within {}.", MAX_WAIT_TIME);
      }
    } catch (InterruptedException e) {
      log.error("Interrupted while waiting for token.", e);
      Thread.currentThread().interrupt();
    }
  }

  private long toSeconds(Duration d) {
    return d.get(ChronoUnit.SECONDS);
  }
}
