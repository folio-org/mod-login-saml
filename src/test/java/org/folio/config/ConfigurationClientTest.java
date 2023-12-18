package org.folio.config;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.MatcherAssert.assertThat;
import org.apache.commons.lang3.builder.DiffResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SamlClientLoader;
import org.folio.config.SamlConfigHolder;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.impl.TestBase;
import org.folio.rest.impl.SamlAPI.UserErrorException;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.profile.BasicUserProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.redirect.RedirectionActionBuilder;
import org.pac4j.saml.client.SAML2Client;
import org.w3c.dom.ls.LSResourceResolver;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class ConfigurationClientTest extends TestBase {
  private static final Logger log = LogManager.getLogger(ConfigurationClientTest.class);
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", TENANT);
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final Header ACCESS_CONTROL_REQ_HEADERS_HEADER = new Header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toString(),
    "content-type,x-okapi-tenant,x-okapi-token");
  private static final Header ACCESS_CONTROL_REQUEST_METHOD_HEADER = new Header(
    HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toString(), "POST");
  private static final String STRIPES_URL = "http://localhost:3000";

  private static final int JSON_MOCK_PORT = NetworkUtils.nextFreePort();
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", "http://localhost:" + JSON_MOCK_PORT);
  public static final int IDP_MOCK_PORT = NetworkUtils.nextFreePort();

  private static final MockJson mock = new MockJson();
  private DataMigrationHelper dataMigrationHelper = new DataMigrationHelper(TENANT_HEADER, TOKEN_HEADER, OKAPI_URL_HEADER);

  @Rule
  public TestName testName = new TestName();
  public final String LOCALHOST_ORIGIN = "http://localhost";

  @BeforeClass
  public static void setupOnce(TestContext context) {
    RestAssured.port = TestBase.MODULE_PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

    DeploymentOptions okapiOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", JSON_MOCK_PORT));

    mock.setMockContent("mock_content_with_delete.json");
    vertx.deployVerticle(mock, okapiOptions)
      .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void setUp() {
    log.info("Running {}", testName.getMethodName());
  }

  @After
  public void tearDown() {
    // Need to clear singleton to maintain test order independence
    SamlConfigHolder.getInstance().removeClient(TENANT);
  }

  @Test
  public void testGetConfiguration(TestContext context) {
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    if (samlConfiguration != null) {
      ConfigurationsClient.getConfiguration(vertx, dataMigrationHelper.getOkapiHeaders())
       .onComplete(context.asyncAssertSuccess(result -> {
             assertTrue(createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
           }));
    }
  }

  @Test
  public void testGetConfigurationWithIds(TestContext context) {
    SamlConfiguration samlConfiguration = mock.getMockPartialContent();
    if (samlConfiguration != null) {
      ConfigurationsClient.getConfigurationWithIds(vertx, dataMigrationHelper.getOkapiHeaders())
        .onComplete(context.asyncAssertSuccess(result -> {
              assertTrue(createDiffResult(result, samlConfiguration).getDiffs().isEmpty());
            }));
    }
  }

  private static DiffResult createDiffResult(SamlConfiguration result, SamlConfiguration samlConfiguration)
  {
    DiffResult diffResult = SamlConfigurationHelper.compareSamlConfigurations(samlConfiguration, result);
    log.info("result = " + SamlConfigurationHelper.printPojo(result));
    log.info("numberOfDiffs = " + diffResult.getNumberOfDiffs());
    return diffResult;
  }
  /*
  @Test
  public void testStoreEntries(TestContext context) throws MalformedURLException {
    Map<String, String> entries = new HashMap<>(5);
    entries.put(SamlConfiguration.IDP_URL_CODE, URI.create("http://localhost:" + IDP_MOCK_PORT + "/xml").toURL().toString());
    entries.put(SamlConfiguration.SAML_ATTRIBUTE_CODE, "UserID");
    //entries.put(SamlConfiguration.SAML_BINDING_CODE, SamlConfigRequest.SamlBinding.POST.toString());
    ////entries.put(SamlConfiguration.USER_PROPERTY_CODE, "externalSystemId");
    //entries.put(SamlConfiguration.OKAPI_URL, URI.create("http://localhost:9131").toURL().toString());
    if (entries != null) {
      ConfigurationsClient.storeEntries(vertx, dataMigrationHelper.getOkapiHeaders(), entries)
        .onComplete(context.asyncAssertSuccess(result -> {
              log.info("result = " + SamlConfigurationHelper.printPojo(result));
              assertTrue(result != null);
            }));
    }
    }*/
}
