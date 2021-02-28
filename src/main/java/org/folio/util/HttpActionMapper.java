package org.folio.util;

import javax.ws.rs.core.Response;
import org.pac4j.core.exception.http.HttpAction;

/**
 * @author rsass
 */
public class HttpActionMapper {
  public static Response toResponse(HttpAction httpAction) {
    return Response.status(httpAction.getCode()).entity(httpAction.getMessage()).build();
  }
}
