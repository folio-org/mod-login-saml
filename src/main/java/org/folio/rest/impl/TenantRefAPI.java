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
    try {
      ConfigurationsDao.verifyOkapiHeaders(OkapiHelper.okapiHeadersWithUrlTo(headers));
      return super.loadData(attributes, tenantId, headers, vertxContext)
        .compose(result ->
          new ConfigurationsDaoImpl().dataMigrationLoadData(vertxContext.owner(), OkapiHelper.okapiHeadersWithUrlTo(headers), true));
    } catch (MissingHeaderException misHeadEx) {
      StringBuilder builder = new StringBuilder("The Okapi headers are not complete. The data migration from mod-configuration is not possible")
        .append(" ").append(misHeadEx.getMessage());
      log.warn(builder.toString());
      return Future.failedFuture(builder.toString());
    }
  }
}
