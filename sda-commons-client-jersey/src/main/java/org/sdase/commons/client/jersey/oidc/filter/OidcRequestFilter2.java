/*
 * Copyright (c) 2018. SDA SE Open Industry Solutions (https://www.sda-se.com).
 *
 * All rights reserved.
 */
package org.sdase.commons.client.jersey.oidc.filter;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import org.sdase.commons.client.jersey.ClientFactory;
import org.sdase.commons.client.jersey.oidc.OidcConfiguration;
import org.sdase.commons.client.jersey.oidc.rest.IssuerClient;
import org.sdase.commons.client.jersey.token.OidcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OidcRequestFilter2 implements ClientRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(OidcRequestFilter2.class);

  private final OidcService oidcService;

  public OidcRequestFilter2(ClientFactory clientFactory, OidcConfiguration oidc) {
    this.oidcService = new OidcService(new IssuerClient(clientFactory, oidc));
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    if (oidcService == null) {
      LOGGER.warn("OidcService not initialized!");
      return;
    }

    String bearerToken = oidcService.getBearerToken();

    if (bearerToken != null && requestContext.getHeaderString(AUTHORIZATION) == null) {
      requestContext.getHeaders().add(AUTHORIZATION, bearerToken);
    }
  }
}
