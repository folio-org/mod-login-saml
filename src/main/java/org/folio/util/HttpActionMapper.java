package org.folio.util;

import org.pac4j.core.exception.HttpAction;

import javax.ws.rs.core.Response;

/**
 * @author rsass
 */
public class HttpActionMapper {
  public static Response toResponse(HttpAction httpAction) {
    return Response.status(httpAction.getCode()).entity(httpAction.getMessage()).build();
  }
}
