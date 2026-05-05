package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.util.Map;
import io.restassured.http.Header;
import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.DataMigrationHelper;
import org.folio.util.MockJsonExtended;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * @author barabaraloehle
 */
@RunWith(VertxUnitRunner.class)
public class TenantRefAPITest extends TestBase {
  private static final Logger log = LogManager.getLogger(TenantRefAPITest.class);

  private static final int MOCK_SERVER_PORT = NetworkUtils.nextFreePort();
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TOKEN);
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:" + MOCK_SERVER_PORT);

  private static final Map<String, String> DATA_MIGRATION_HELPER_HEADERS = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER)
    .getHeaders();
  private static final Map<String, String> DATA_MIGRATION_HELPER_HEADERS_INCOMPLETE = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER)
    .getIncompleteHeaders();
  private static final MockJsonExtended mock = new MockJsonExtended();

  @Rule
  public TestName testName = new TestName();

  @Before
  public void setupOnce(TestContext context) {
    log.info("Running {}", testName.getMethodName());
    RestAssured.port = TestBase.modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MOCK_SERVER_PORT));

    vertx.deployVerticle(mock, okapiOptions)
      .onComplete(context.asyncAssertSuccess());
  }

 @Before
  public void setUp() {
    mock.resetNecessaryLists();
    log.info("Running {}", testName.getMethodName());
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
    deleteAllConfigurationRecords(vertx);
  }

  @Test
  public void loadDataWithMockEmptyDatabaseWithDeletionFailure(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    // Without 'mock.setMockIds();', because the data of mod-configuration has not been downloaded.
    String expectedText = "After deletion of the data of mod-configuration the compared Objects are different";
    TenantAPI tenantAPI = new TenantRefAPI();
    tenantAPI.postTenantSync(TENANT_ATTRIBUTES_UPGRADE, DATA_MIGRATION_HELPER_HEADERS, context.asyncAssertSuccess(result -> {
      assertThat(result.getStatus(), is(400));
      assertThat((String) result.getEntity(), startsWith(expectedText));
      assertThat(mock.getRequestedUrlList().size(), not(mock.getMockIdsHolder().size()));
    }), vertx.getOrCreateContext());
  }

  @Test
  public void loadDataWithMockEmptyDatabase(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    mock.setMockIds();
    TenantAPI tenantAPI = new TenantRefAPI();
    tenantAPI.postTenantSync(TENANT_ATTRIBUTES_UPGRADE, DATA_MIGRATION_HELPER_HEADERS, context.asyncAssertSuccess(result -> {
      assertThat(result.getStatus(), is(204));
      assertThatRequestedUrlListContainsAllPartialContentIds(mock);
    }), vertx.getOrCreateContext());
  }

  @Test
  public void loadNoDataWithMock400EmptyDatabase(TestContext context) {
    mock.setMockContent("mock_400.json");
    mock.setMockIds();
    TenantAPI tenantAPI = new TenantRefAPI();
    tenantAPI.postTenantSync(TENANT_ATTRIBUTES_UPGRADE, DATA_MIGRATION_HELPER_HEADERS, context.asyncAssertSuccess(result -> {
      assertThat(result.getStatus(), is(400));
      assertThat((String) result.getEntity(), is("Response status code 400 is not equal to 200 - { }"));
    }), vertx.getOrCreateContext());
  }

  @Test
  public void loadNoDataWithMock200EmptyDatabase(TestContext context) {
    mock.setMockContent("mock_200_empty.json");
    mock.setMockIds();
    TenantAPI tenantAPI = new TenantRefAPI();
    tenantAPI.postTenantSync(TENANT_ATTRIBUTES_UPGRADE, DATA_MIGRATION_HELPER_HEADERS, context.asyncAssertSuccess(result -> {
      assertThat(result.getStatus(), is(204));
      assertThatRequestedUrlListContainsAllPartialContentIds(mock);
    }), vertx.getOrCreateContext());
  }

  @Test
  public void dontLoadDataOnInstall(TestContext context) {
    TenantAPI tenantAPI = new TenantRefAPI();
    tenantAPI.postTenantSync(TENANT_ATTRIBUTES_INSTALLATION, DATA_MIGRATION_HELPER_HEADERS, context.asyncAssertSuccess(result ->
      assertThat(result.getStatus(), is(204))), vertx.getOrCreateContext());
  }

  @Test
  public void loadNoDataWithMock200EmptyDatabaseIncompleteHeaders(TestContext context) {
    mock.setMockContent("mock_200_empty.json");
    mock.setMockIds();
    String expectedText = "The Okapi headers are not complete. The data migration from mod-configuration is not possible: Missing Okapi URL";
    TenantAPI tenantAPI = new TenantRefAPI();
    tenantAPI.postTenantSync(TENANT_ATTRIBUTES_UPGRADE, DATA_MIGRATION_HELPER_HEADERS_INCOMPLETE, context.asyncAssertSuccess(result -> {
      assertThat(result.getStatus(), is(400));
      assertThat((String) result.getEntity(), is(expectedText));
    }), vertx.getOrCreateContext());
  }

  public static void assertThatRequestedUrlListContainsAllPartialContentIds(MockJsonExtended mock) {
    var partialContentIds = mock.getMockIdsHolder();
    if (partialContentIds.isEmpty() && mock.getRequestedUrlList().isEmpty()) {
      return;
    }
    assertThat(mock.getRequestedUrlList(), containsInAnyOrder(partialContentIds.toArray()));
  }
}
