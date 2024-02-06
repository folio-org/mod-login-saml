package org.folio.dao.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.vertx.core.Future;
import io.vertx.core.CompositeFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.rest.impl.TestBase;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.DataMigrationHelper;
import org.folio.util.MockJsonExtended;
import org.folio.util.OkapiHelper;
import org.folio.util.SamlConfigurationHelper;
import org.folio.util.model.OkapiHeaders;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
/**
 * @author barbaraloehle
 */
@RunWith(VertxUnitRunner.class)
public class ConfigurationsDaoImplTest extends TestBase {
  private static final Logger log = LogManager.getLogger(ConfigurationsDaoImplTest.class);

  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);

  private static final int JSON_MOCK_PORT = NetworkUtils.nextFreePort();
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:" + JSON_MOCK_PORT);

  private static final MockJsonExtended mock = new MockJsonExtended();
  private DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);
  private ConfigurationsDao configurationsDao = new ConfigurationsDaoImpl();

  @Rule
  public TestName testName = new TestName();
  public final String LOCALHOST_ORIGIN = "http://localhost";

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", JSON_MOCK_PORT));

    mock.setMockContent("mock_200_empty.json");
    vertx.deployVerticle(mock, okapiOptions)
    .compose(x -> postTenantWithToken())
    .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void setUp(TestContext context) {
    deleteAllConfigurationRecords(vertx);
    log.info("Running {}", testName.getMethodName());
    mock.setMockContent("mock_content.json");
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
  }

  @Test
  public void DataMigrationServerStatus400(TestContext context) {
    Async async = context.async();
    mock.setMockContent("mock_400.json");
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onFailure(cause -> {
        context.assertEquals("Response status code 400 is not equal to 200", cause.getMessage());
        async.complete();
      });
  }

  @Test
  public void DataMigrationWithoutData(TestContext context) {
    Async async = context.async();
    mock.setMockContent("mock_nouser_db.json");
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onFailure(cause -> {
        context.assertEquals("Response status code 404 is not equal to 200", cause.getMessage());
        async.complete();
      });
  }

  @Test
  public void DataMigrationServerStatusSuccess(TestContext context) {
    Async async = context.async();
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onComplete(context.asyncAssertSuccess(result -> {
         assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
         async.complete();
      }));
  }

  @Test
  public void DataMigrationServerStatusSuccessExistentEntry(TestContext context) {
    Async async = context.async();
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    PostgresClient.getInstance(vertx, createOkapiHeaders().getTenant())
      .upsert("configuration", null, samlConfiguration, true);
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
        async.complete();
      }));
  }

  @Test
  public void DataMigrationServerStatusSuccess2ExistentEntries(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    mock.setMockContent("mock_example_entries.json");
    SamlConfiguration samlConfigurationAdditional = mock.getMockPartialContent();

    List<Future<String>> futureDbIds = new ArrayList<Future<String>>();
    futureDbIds.add(PostgresClient.getInstance(vertx, createOkapiHeaders().getTenant())
      .upsert("configuration", null, samlConfiguration, true));
    futureDbIds.add(PostgresClient.getInstance(vertx, createOkapiHeaders().getTenant())
      .upsert("configuration", null, samlConfigurationAdditional, true));

    Future.all(futureDbIds).onComplete(compositeResult -> {
      if (compositeResult.succeeded()) {
        mock.setMockContent("mock_content_with_delete.json");
        configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
          .onComplete(context.asyncAssertFailure(cause ->
            assertThat(cause.getMessage(), startsWith("Migration: Number of records are not unique. Instead the number is : 2"))));
      }
    });
  }

  private static OkapiHeaders createOkapiHeaders(){
    Map<String, String> parsedHeaders = new HashMap<String, String>();
    parsedHeaders.put(TENANT_HEADER.getName(), TENANT_HEADER.getValue());
    parsedHeaders.put(TOKEN_HEADER.getName(), TOKEN_HEADER.getValue());
    parsedHeaders.put(OKAPI_URL_HEADER.getName(), OKAPI_URL_HEADER.getValue());
    return OkapiHelper.okapiHeaders(parsedHeaders);
  }

  public void createDatabaseEntry(TestContext context, SamlConfiguration samlConfiguration) {

    Async async = context.async();
    PostgresClient.getInstance(vertx, createOkapiHeaders().getTenant())
      .upsert("configuration", null, samlConfiguration, true);

    async.awaitSuccess(TimeUnit.MILLISECONDS.convert(10L, TimeUnit.MINUTES));
  }
}
