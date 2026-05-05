package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.RestAssured;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlConfigHolder;
import org.folio.rest.tools.utils.NetworkUtils;
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
public class TenantRefAPITenantClientTest extends TestBase {
  private static final Logger log = LogManager.getLogger(TenantRefAPITest.class);

  private static final int MOCK_SERVER_PORT = NetworkUtils.nextFreePort();
  private static final String MOCK_SERVER_URL = "http://localhost:" + MOCK_SERVER_PORT;
  public static final MockJsonExtended mockJsonExtended = new MockJsonExtended();

  @Rule
  public TestName testName = new TestName();

  @Before
  public void setupOnce(TestContext context) {
    log.info("Running {}", testName.getMethodName());
    RestAssured.port = TestBase.modulePort;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MOCK_SERVER_PORT));

    vertx.deployVerticle(mockJsonExtended, okapiOptions)
      .onComplete(context.asyncAssertSuccess());
  }

 @Before
  public void setUp() {
    mockJsonExtended.resetNecessaryLists();
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
    mockJsonExtended.setMockContent("mock_content_with_delete.json");
    // Without 'mock.setMockIds();', because the data of mod-configuration has not been downloaded.
    String expectedText = "After deletion of the data of mod-configuration the compared Objects are different";
    postTenantUpgrade(TENANT_ATTRIBUTES_UPGRADE, MOCK_SERVER_URL)
      .onComplete(context.asyncAssertFailure(cause -> {
        assertThat(cause.getMessage(), containsString(expectedText));
        assertThat(mockJsonExtended.getRequestedUrlList().size(), not(mockJsonExtended.getMockIdsHolder().size()));
      }));
  }

  @Test
  public void loadDataWithMockEmptyDatabase(TestContext context) {
    mockJsonExtended.setMockContent("mock_content_with_delete.json");
    mockJsonExtended.setMockIds();
    postTenantUpgrade(TENANT_ATTRIBUTES_UPGRADE, MOCK_SERVER_URL)
      .onComplete(context.asyncAssertSuccess(res -> {
        TenantRefAPITest.assertThatRequestedUrlListContainsAllPartialContentIds(mockJsonExtended);
      }));
  }

  @Test
  public void loadNoDataWithMock400EmptyDatabase(TestContext context) {
    mockJsonExtended.setMockContent("mock_400.json");
    mockJsonExtended.setMockIds();
    postTenantUpgrade(TENANT_ATTRIBUTES_UPGRADE, MOCK_SERVER_URL)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Response status code 400 is not equal to 200"))));
  }

  @Test
  public void loadNoDataWithMock200EmptyDatabase(TestContext context) {
    mockJsonExtended.setMockContent("mock_200_empty.json");
    mockJsonExtended.setMockIds();
    postTenantUpgrade(TENANT_ATTRIBUTES_UPGRADE, MOCK_SERVER_URL)
      .onComplete(context.asyncAssertSuccess(res -> {
        TenantRefAPITest.assertThatRequestedUrlListContainsAllPartialContentIds(mockJsonExtended);
      }));
  }

  @Test
  public void dontLoadDataOnInstall(TestContext context) {
    mockJsonExtended.setMockContent("mock_400.json");
    mockJsonExtended.setMockIds();
    postTenantInstall(TENANT_ATTRIBUTES_INSTALLATION, MOCK_SERVER_URL)
      .onComplete(context.asyncAssertSuccess());
  }
}
