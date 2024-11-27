package org.folio.rest.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
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
import org.junit.Ignore;
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
    mock.resetReceivedData();
    mock.resetRequestedUrlList();
    log.info("Running {}", testName.getMethodName());
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
    deleteAllConfigurationRecords(vertx);
  }

  @Ignore("https://folio-org.atlassian.net/browse/MODLOGSAML-203")
  @Test
  public void loadDataWithMockEmptyDatabaseWithDeletionFailure(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    String expectedText = "After deletion of the data of mod-configuration the compared Objects are different";
    postTenantUpgrade("http://localhost:" + MOCK_SERVER_PORT)
      .onComplete(context.asyncAssertFailure(cause -> {
        assertThat(cause.getMessage(), containsString(expectedText));
        assertThatRequestedUrlListContainsAllPartialContentIds();
      }));
  }

  @Ignore("https://folio-org.atlassian.net/browse/MODLOGSAML-203")
  @Test
  public void loadDataWithMockEmptyDatabase(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    mock.setMockIds();
    postTenantUpgrade("http://localhost:" + MOCK_SERVER_PORT)
      .onComplete(context.asyncAssertSuccess(res -> {
        assertThatRequestedUrlListContainsAllPartialContentIds();
      }));
  }

  @Ignore("https://folio-org.atlassian.net/browse/MODLOGSAML-203")
  @Test
  public void loadNoDataWithMock400EmptyDatabase(TestContext context) {
    mock.setMockContent("mock_400.json");
    mock.setMockIds();
    postTenantUpgrade("http://localhost:" + MOCK_SERVER_PORT)
      .onComplete(context.asyncAssertFailure(cause ->
        assertThat(cause.getMessage(), startsWith("Response status code 400 is not equal to 200"))));
  }

  @Ignore("https://folio-org.atlassian.net/browse/MODLOGSAML-203")
  @Test
  public void loadNoDataWithMock200EmptyDatabase(TestContext context) {
    mock.setMockContent("mock_200_empty.json");
    mock.setMockIds();
    postTenantUpgrade("http://localhost:" + MOCK_SERVER_PORT)
      .onComplete(context.asyncAssertSuccess(res -> {
        assertThatRequestedUrlListContainsAllPartialContentIds();
      }));
  }

  @Test
  public void dontLoadDataOnInstall(TestContext context) {
    mock.setMockContent("mock_400.json");
    mock.setMockIds();
    postTenantInstall("http://localhost:" + MOCK_SERVER_PORT)
      .onComplete(context.asyncAssertSuccess());
  }

  private void assertThatRequestedUrlListContainsAllPartialContentIds() {
    var partialContentIds = mock.getMockPartialContentIds();
    if (partialContentIds.isEmpty()) {
      return;
    }
    assertThat(mock.getRequestedUrlList(), containsInAnyOrder(partialContentIds.toArray()));
  }
}
