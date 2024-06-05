package org.folio.rest.impl;

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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * @author barabaraloehle
 */
@RunWith(VertxUnitRunner.class)
public class TenantRefAPITest extends TestBase {
  private static final Logger log = LogManager.getLogger(TenantRefAPITest.class);

  private static final String PERMISSIONS_HEADER = TENANT + "-permissons"; //for testing org.folio.util.model.OkapiHeaders.java
  private static final int MOCK_SERVER_PORT = NetworkUtils.nextFreePort();
  private static final MockJsonExtended mock = new MockJsonExtended();

  @Rule
  public TestName testName = new TestName();
  public final String LOCALHOST_ORIGIN = "http://localhost";

  @Before
  public void setupOnce(TestContext context) {
    log.info("Running {}", testName.getMethodName());
    RestAssured.port = TestBase.MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", MOCK_SERVER_PORT));

    vertx.deployVerticle(mock, okapiOptions)
      .onComplete(context.asyncAssertSuccess());
  }

 @Before
  public void setUp(TestContext context) {
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

  @Test
  public void loadDataWithMockEmptyDatabaseWithDeletionFailure(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    boolean expectedBoolean = true;
    String expectedText = "After deletion of the data of mod-configuration the compared Objects are different";
    postTenantExtendedWithToken("http://localhost:" + MOCK_SERVER_PORT, PERMISSIONS_HEADER)
      .onComplete(context.asyncAssertFailure(cause -> {
        assertThat(cause.getMessage(), containsString(expectedText));
        assertEquals(expectedBoolean, mock.getRequestedUrlList().containsAll(mock.getMockPartialContentIds()));
        mock.resetRequestedUrlList();
      }));
  }

  @Test
  public void loadDataWithMockEmptyDatabase(TestContext context) {
    mock.setMockContent("mock_content_with_delete.json");
    boolean expectedBoolean = true;
    mock.setMockIds();
    postTenantExtendedWithToken("http://localhost:" + MOCK_SERVER_PORT, PERMISSIONS_HEADER)
      .onComplete(context.asyncAssertSuccess(res -> {
        assertEquals(expectedBoolean, mock.getRequestedUrlList().containsAll(mock.getMockPartialContentIds()));
        mock.resetRequestedUrlList();
      }));
  }
}
