package org.folio.rest.impl;

import java.util.Map;
import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.impl.ConfigurationsDaoImpl;
import org.folio.config.SamlConfigHolder;//
import org.folio.config.model.SamlClientComposite;//
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;
import org.folio.util.OkapiHelper;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
    Map<String, String> headers, Context vertxContext) {
    log.info("postTenant");

    return super.loadData(attributes, tenantId, headers, vertxContext)
      .compose(res -> new ConfigurationsDaoImpl().dataMigrationLoadData(vertxContext.owner(), OkapiHelper.okapiHeaders(headers), true));
  }   
}
