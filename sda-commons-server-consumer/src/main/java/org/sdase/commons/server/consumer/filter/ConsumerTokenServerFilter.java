package org.sdase.commons.server.consumer.filter;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Priority;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import org.sdase.commons.shared.api.error.ApiException;
import org.sdase.commons.shared.tracing.ConsumerTracing;
import org.slf4j.MDC;

/** A request filter that detects, verifies and provides the consumer token in incoming requests. */
@Priority(Priorities.AUTHENTICATION - 10) // Before Access-Token authentication
public class ConsumerTokenServerFilter implements ContainerRequestFilter {

  private final boolean requireIdentifiedConsumer;
  private final List<Pattern> excludePatterns;

  private final List<String> excludeRegex;

  /**
   * @param requireIdentifiedConsumer if an identified customer is required to fulfill requests
   * @param excludeRegex a list of regex pattern for paths that are excluded from the filter
   */
  public ConsumerTokenServerFilter(boolean requireIdentifiedConsumer, List<String> excludeRegex) {
    this.requireIdentifiedConsumer = requireIdentifiedConsumer;
    this.excludeRegex =
        excludeRegex == null ? Collections.emptyList() : new ArrayList<>(excludeRegex);
    this.excludePatterns = this.excludeRegex.stream().map(Pattern::compile).collect(toList());
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {

    // In case of OPTIONS, no headers can be provided. Usually OPTION requests are from browsers for
    // CORS.
    if (HttpMethod.OPTIONS.equals(requestContext.getMethod())) {
      return;
    }

    Optional<String> consumerToken = extractConsumerTokenFromRequest(requestContext);
    Optional<String> consumerName = consumerToken.map(this::extractConsumerName);
    consumerToken.ifPresent(token -> this.addConsumerTokenToRequest(requestContext, token));
    consumerName.ifPresent(this::addConsumerNameToMdc);
    consumerName.ifPresent(name -> this.addConsumerNameToRequest(requestContext, name));

    if (requireIdentifiedConsumer && !consumerName.isPresent()) {
      String path = requestContext.getUriInfo().getPath();
      boolean pathExcluded = excludePatterns.stream().anyMatch(p -> p.matcher(path).matches());
      if (!pathExcluded) {
        throw ApiException.builder()
            .httpCode(401)
            .title("Consumer token is required to access this resource.")
            .build();
      }
    }
  }

  private Optional<String> extractConsumerTokenFromRequest(ContainerRequestContext requestContext) {
    String consumerToken = requestContext.getHeaderString(ConsumerTracing.TOKEN_HEADER);
    if (consumerToken == null || consumerToken.trim().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(consumerToken);
  }

  private String extractConsumerName(String consumerToken) {
    // TODO: Verify and parse token (for now the token is the consumer name)
    return consumerToken;
  }

  private void addConsumerTokenToRequest(
      ContainerRequestContext requestContext, String consumerToken) {
    requestContext.setProperty(ConsumerTracing.TOKEN_ATTRIBUTE, consumerToken);
  }

  private void addConsumerNameToMdc(String consumerName) {
    if (MDC.getMDCAdapter() != null) {
      MDC.put(ConsumerTracing.NAME_MDC_KEY, consumerName);
    }
  }

  private void addConsumerNameToRequest(
      ContainerRequestContext requestContext, String consumerName) {
    requestContext.setProperty(ConsumerTracing.NAME_ATTRIBUTE, consumerName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConsumerTokenServerFilter that = (ConsumerTokenServerFilter) o;
    return requireIdentifiedConsumer == that.requireIdentifiedConsumer
        && Objects.equals(this.excludeRegex, that.excludeRegex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requireIdentifiedConsumer, excludeRegex);
  }
}
