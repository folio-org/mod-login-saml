package org.folio.dao.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlConfiguration;
import org.folio.dao.ConfigurationsDao;
import org.folio.rest.impl.TestBase;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.MockJsonExtended;
import org.folio.util.OkapiHelper;
import org.folio.util.SamlConfigurationHelper;
import org.folio.util.SamlConfigurationUtil;
import org.folio.util.model.OkapiHeaders;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
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
  private ConfigurationsDaoImpl configurationsDaoImpl = new ConfigurationsDaoImpl();
  private ConfigurationsDao configurationsDao = configurationsDaoImpl;

  @Rule
  public TestName testName = new TestName();
  public static final String LOCALHOST_ORIGIN = "http://localhost";

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", JSON_MOCK_PORT));

    mock.setMockContent("mock_200_empty.json");
    vertx.deployVerticle(mock, okapiOptions)
      .compose(x -> postTenant())
      //.compose(x -> postTenantWithToken())
      .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void setUp(TestContext context) {
    deleteDBEntries(context);
    mock.resetReceivedData();
    mock.resetRequestedUrlList();
    log.info("Running {}", testName.getMethodName());
    mock.setMockContent("mock_content.json");
  }

  @After
  public void tearDown(TestContext context) {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
  }

  @Test
  public void dataMigrationServerStatus400(TestContext context) {
    mock.setMockContent("mock_400.json");
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onFailure(cause -> {
        context.assertEquals("Response status code 400 is not equal to 200", cause.getMessage());
      });
  }

  @Test
  public void dataMigrationWithoutDataWithoutAndWithDeletion(TestContext context) {
    mock.setMockContent("mock_200_empty.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();

    testDataMigration(context, samlConfiguration, false);
    testDataMigration(context, samlConfiguration, true);
  }

  private void testDataMigration(TestContext context, SamlConfiguration samlConfiguration, boolean withDeletion) {
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), withDeletion)
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
      }));
  }

  @Test
  public void dataMigrationEmptyDBWithoutDeletion(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();

    testDataMigration(context, samlConfiguration, false);
  }

  @Test
  public void dataMigrationServerStatusSuccessWithDeletion(TestContext context) {
    boolean expectedBoolean = true;
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    mock.setMockIds();

    configurationsDao.dataMigration(vertx, createOkapiHeaders(), true)
      .onComplete(context.asyncAssertSuccess(result -> {
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
        assertEquals(expectedBoolean, mock.getRequestedUrlList().containsAll(mock.getMockPartialContentIds()));
        log.info("All entries are deleted");
        mock.resetRequestedUrlList();
      }));
  }

  @Test
  public void dataMigrationServerStatusSuccessExistentEntryWithAndWithoutDeletion(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    createDatabaseEntry(context, samlConfiguration);

    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onComplete(context.asyncAssertSuccess(result ->
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty())));

    configurationsDao.dataMigration(vertx, createOkapiHeaders(), true)
      .onComplete(context.asyncAssertSuccess(result ->
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty())));
  }

  @Test
  public void dataMigrationServerStatusSuccess2ExistentEntriesWithAndWithoutDeletion(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    createDatabaseEntry(context, samlConfiguration);

    mock.setMockContent("mock_example_entries.json");
    SamlConfiguration samlConfigurationAdditional = mock.getMockPartialContent();
    createDatabaseEntry(context, samlConfigurationAdditional);

    mock.setMockContent("mock_content_with_delete.json");
    configurationsDao.dataMigration(vertx, createOkapiHeaders(), false)
      .onComplete(context.asyncAssertFailure(cause ->
         assertThat(cause.getMessage(), startsWith("Migration: Number of records are not unique. Instead the number is : 2"))));

    configurationsDao.dataMigration(vertx, createOkapiHeaders(), true)
      .onComplete(context.asyncAssertFailure(cause ->
          assertThat(cause.getMessage(), startsWith("Migration: Number of records are not unique. Instead the number is : 2"))));
   }

  @Test
  public void getConfigurationDataEmptyDB(TestContext context) {
    configurationsDao.getConfiguration(vertx, createOkapiHeaders(), false)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("There is an empty DB"))));
  }

  @Test
  public void getConfigurationDataEmptyDBWithPut(TestContext context) {
    SamlConfiguration samlConfiguration = new SamlConfiguration();
    configurationsDao.getConfiguration(vertx, createOkapiHeaders(), true)
      .onComplete(context.asyncAssertSuccess(result ->
        assertTrue(SamlConfigurationHelper.createDiffResult(result, samlConfiguration).getDiffs().isEmpty())));
  }

  @Test
  public void getConfigurationData2ExistentEntries(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    createDatabaseEntry(context, samlConfiguration);

    mock.setMockContent("mock_example_entries.json");
    SamlConfiguration samlConfigurationAdditional = mock.getMockPartialContent();
    createDatabaseEntry(context, samlConfigurationAdditional);

    configurationsDao.getConfiguration(vertx, createOkapiHeaders(), false)
      .onComplete(context.asyncAssertFailure(cause -> {
         assertThat(cause.getMessage(), startsWith("Number of records are not unique. The number is"));
         log.info("Number of records are not unique.");
      }));
  }

  @Test
  public void testSamlConfigurationUpdateEmptyDatabase(TestContext context) {
    mock.setMockContent("mock_content_legacy.json");
    SamlConfiguration samlConfigurationToStoreInDatabase = mock.getMockPartialContent();
    Map<String, String> map2Update = SamlConfigurationUtil.samlConfiguration2Map(samlConfigurationToStoreInDatabase);
    int expectedInt = 0;

    configurationsDao.storeEntry(vertx, createOkapiHeaders(), map2Update)
      .onComplete(context.asyncAssertSuccess(result -> {
            assertEquals(expectedInt, SamlConfigurationHelper.createDiffResult(result, samlConfigurationToStoreInDatabase).getNumberOfDiffs());
      }));
  }

  @Test
  public void testSamlConfigurationUpdateEmptyDatabaseIncorrectCode(TestContext context) {
    mock.setMockContent("mock_example_entries.json");
    Map<String, String> map2Update = SamlConfigurationUtil.samlConfiguration2Map(mock.getMockPartialContent());
    map2Update.put("incorrect", "This is the value of an incorrect code");

    configurationsDao.storeEntry(vertx, createOkapiHeaders(), map2Update)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Switch: Incorrect code. The code value is : incorrect"))));
  }

  @Test
  public void testSamlConfigurationUpdateEmptyDatabaseComplete(TestContext context) {
    mock.setMockContent("mock_content_legacy.json");
    SamlConfiguration samlConfigurationToStoreInDatabase = mock.getMockPartialContent();
    Map<String, String> map2Update = SamlConfigurationUtil.samlConfiguration2Map(mock.getMockPartialContent());
    map2Update.put(SamlConfiguration.IDP_METADATA_CODE, "SamlConfiguration.IDP_METADATA_CODE value");
    map2Update.put(SamlConfiguration.SAML_ATTRIBUTE_CODE, "SamlConfiguration.SAML_ATTRIBUTE_CODE value");
    map2Update.put(SamlConfiguration.USER_PROPERTY_CODE, "SamlConfiguration.USER_PROPERTY_CODE value");
    int expectedInt = 3;

    configurationsDao.storeEntry(vertx, createOkapiHeaders(), map2Update)
      .onComplete(context.asyncAssertSuccess(result -> {
            assertEquals(expectedInt, SamlConfigurationHelper.createDiffResult(result, samlConfigurationToStoreInDatabase).getNumberOfDiffs());
      }));
  }

  @Test
  public void testSamlConfigurationUpdate(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    SamlConfiguration samlConfigurationBase = mock.getMockPartialContent();

    mock.setMockContent("mock_example_entries.json");
    Map<String, String> map2Update = SamlConfigurationUtil.samlConfiguration2Map(mock.getMockPartialContent());
    int expectedInt = 4;

    createDatabaseEntry(context, samlConfigurationBase);
    configurationsDao.storeEntry(vertx, createOkapiHeaders(), map2Update)
      .onComplete(context.asyncAssertSuccess(result -> {
        assertEquals(expectedInt, SamlConfigurationHelper.createDiffResult(result, samlConfigurationBase).getNumberOfDiffs());
    }));
  }

  private static OkapiHeaders createOkapiHeaders(){
    Map<String, String> parsedHeaders = new HashMap<String, String>();
    parsedHeaders.put(TENANT_HEADER.getName(), TENANT_HEADER.getValue());
    parsedHeaders.put(TOKEN_HEADER.getName(), TOKEN_HEADER.getValue());
    parsedHeaders.put(OKAPI_URL_HEADER.getName(), OKAPI_URL_HEADER.getValue());
    return OkapiHelper.okapiHeaders(parsedHeaders);
  }

  public Future<String> createDatabaseEntry(TestContext context, SamlConfiguration samlConfiguration) {
    Async async = context.async();
    Future<String> result = PostgresClient.getInstance(vertx, createOkapiHeaders().getTenant())
      .upsert("configuration", null, samlConfiguration, true)
      .onComplete(context.asyncAssertSuccess(res -> async.complete()));
    async.awaitSuccess(TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS));
    return result;
  }

  public void deleteDBEntries(TestContext context) {
    Async async = context.async();
    deleteAllConfigurationRecords(vertx)
      .onComplete(context.asyncAssertSuccess(res -> async.complete()));
    async.awaitSuccess(TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS));
  }
}
