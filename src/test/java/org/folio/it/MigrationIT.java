package org.folio.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import java.nio.file.Path;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Test that the shaded fat jar file and the Dockerfile container properly work.
 */
public class MigrationIT {
  private static final Logger LOG = LoggerFactory.getLogger(MigrationIT.class);

  /** set true for debugging. */
  public static final boolean IS_LOG_ENABLED = false;

  public static final Network NETWORK = Network.newNetwork();

  @ClassRule
  public static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(PostgresTesterContainer.getImageName())
        .withNetwork(NETWORK)
        .withNetworkAliases("mypostgres")
        .withExposedPorts(5432)
        .withUsername("username")
        .withPassword("password")
        .withDatabaseName("postgres");

  @ClassRule
  public static final GenericContainer<?> MOD_LOGIN_SAML =
      new GenericContainer<>(
          new ImageFromDockerfile("mod-login-saml").withDockerfile(Path.of("./Dockerfile")))
        .withNetwork(NETWORK)
        .withExposedPorts(8081)
        .withAccessToHost(true)
        .withEnv("DB_HOST", "mypostgres")
        .withEnv("DB_PORT", "5432")
        .withEnv("DB_USERNAME", "username")
        .withEnv("DB_PASSWORD", "password")
        .withEnv("DB_DATABASE", "postgres")
        .dependsOn(POSTGRES);

  @ClassRule
  public static final WireMockRule OKAPI_MOCK =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
        .notifier(new ConsoleNotifier(IS_LOG_ENABLED))
        .dynamicPort());

  @BeforeClass
  public static void beforeClass() {
    Testcontainers.exposeHostPorts(OKAPI_MOCK.port());
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + MOD_LOGIN_SAML.getHost() + ":" + MOD_LOGIN_SAML.getFirstMappedPort();
    if (IS_LOG_ENABLED) {
      POSTGRES.followOutput(
          new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("postgres"));
      MOD_LOGIN_SAML.followOutput(
          new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("mod-login-saml"));
    }

  }

  @Test
  public void health() {
    given()
      .when()
      .get("/admin/health")
      .then()
      .statusCode(200)
      .body(is("\"OK\""));
  }

  @Test
  public void installAndMigrate() {
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeader(XOkapiHeaders.URL, String.format("http://host.testcontainers.internal:" + OKAPI_MOCK.port()))
        .addHeader(XOkapiHeaders.TENANT, "diku")
        .addHeader(XOkapiHeaders.TOKEN, "t.oke.n")
        .setContentType(ContentType.JSON)
        .build();

    postTenant("""
        {
          "module_to": "mod-login-saml:1.0.0"
        }
        """);

    stubFor(WireMock.get(urlPathEqualTo("/configurations/entries"))
        .inScenario("scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(WireMock
            .ok()
            .withHeader("Content-type", "application/json")
            .withBody("""
                {
                  "configs": [
                    {
                      "id": "a0eead4f-de97-437c-9cb7-09966ce50e49",
                      "module": "LOGIN-SAML",
                      "configName": "saml",
                      "code": "idp.url",
                      "value": "https://idp.example.com"
                    }
                  ]
                }
                """)));
    stubFor(WireMock.delete("/configurations/entries/a0eead4f-de97-437c-9cb7-09966ce50e49")
        .inScenario("scenario")
        .willReturn(WireMock.noContent())
        .willSetStateTo("2"));
    stubFor(WireMock.get(urlPathEqualTo("/configurations/entries"))
        .inScenario("scenario")
        .whenScenarioStateIs("2")
        .willReturn(WireMock
            .ok()
            .withHeader("Content-type", "application/json")
            .withBody("""
                {
                  "configs": []
                }
                """)));

    postTenant("""
        {
          "module_from": "mod-login-saml:1.0.0",
          "module_to": "mod-login-saml:999.0.0"
        }
        """);

    given().when()
        .get("/saml/configuration")
        .then()
        .statusCode(200)
        .body("idpUrl", is("https://idp.example.com"));
  }

  void postTenant(String body) {
    String location =
        given()
        .body(body)
        .when()
        .post("/_/tenant")
        .then()
        .statusCode(201)
        .extract()
        .header("Location");

    given()
    .when()
    .get(location + "?wait=60000")
    .then()
    .statusCode(200)  // getting job record succeeds
    .body("complete", is(true))  // job is complete
    .body("error", is(nullValue()));  // job has succeeded without error
  }
}
