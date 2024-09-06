package org.folio.rest.impl;

import java.util.Map;

import io.vertx.core.Context;
import io.vertx.core.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ConfigurationsDao;
import org.folio.dao.ConfigurationsDao.MissingHeaderException;
import org.folio.dao.impl.ConfigurationsDaoImpl;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.util.OkapiHelper;

public class TenantRefAPI extends TenantAPI {

  private static final Logger log = LogManager.getLogger();

  @Validate
  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
      Map<String, String> headers, Context vertxContext) {
    log.info("TenantRefAPI::loadData");
    return super.loadData(attributes, tenantId, headers, vertxContext)
        .compose(result -> {
          if (attributes.getModuleFrom() == null) {
            return Future.succeededFuture(0);
          }
          return configurationsMigration(headers, vertxContext);
        });
  }

  private Future<Integer> configurationsMigration(Map<String, String> headers, Context vertxContext) {
    try {
      var okapiHeaders = OkapiHelper.okapiHeadersWithUrlTo(headers);
      ConfigurationsDao.verifyOkapiHeaders(okapiHeaders);
      return new ConfigurationsDaoImpl().dataMigrationLoadData(vertxContext.owner(), okapiHeaders, true);
    } catch (MissingHeaderException misHeadEx) {
      String errorMessage = "The Okapi headers are not complete. "
          + "The data migration from mod-configuration is not possible: " + misHeadEx.getMessage();
      log.error("{}", errorMessage);
      return Future.failedFuture(errorMessage);
    }

  }
}
