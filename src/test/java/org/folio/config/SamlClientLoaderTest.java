package org.folio.config;

import static org.junit.Assert.*;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.net.MalformedURLException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.saml.client.SAML2Client;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

public class SamlClientLoaderTest {

  @Test
  public void configureSaml2ClientTest() throws MalformedURLException {
    String okaiUrl = "okaiUrl";
    String tenantId = "tenantId";
    String keystorePassword = "keystorePassword";
    String privateKeyPassword = "privateKeyPassword";
    UrlResource idpUrlResource = new UrlResource("http://localhost:80");
    ByteArrayResource keystoreResource = new ByteArrayResource(new byte[]{});
    String samlBinding = "samlBinding";
    Resource idpMetadata = new UrlResource("http://localhost:80");
     Context vertxContext = new Context() {
       @Override
       public void runOnContext(Handler<Void> handler) {

       }

       @Override
       public <T> void executeBlocking(Handler<Promise<T>> handler, boolean b,
         Handler<AsyncResult<@Nullable T>> handler1) {

       }

       @Override
       public <T> void executeBlocking(Handler<Promise<T>> handler,
         Handler<AsyncResult<@Nullable T>> handler1) {

       }

       @Override
       public <T> Future<@Nullable T> executeBlocking(
         Handler<Promise<T>> handler, boolean b) {
         return null;
       }

       @Override
       public <T> Future<T> executeBlocking(
         Handler<Promise<T>> handler) {
         return null;
       }

       @Override
       public String deploymentID() {
         return null;
       }

       @Override
       public @Nullable JsonObject config() {
         return new JsonObject();
       }

       @Override
       public List<String> processArgs() {
         return null;
       }

       @Override
       public boolean isEventLoopContext() {
         return false;
       }

       @Override
       public boolean isWorkerContext() {
         return false;
       }

       @Override
       public <T> T get(Object o) {
         return null;
       }

       @Override
       public void put(Object o, Object o1) {

       }

       @Override
       public boolean remove(Object o) {
         return false;
       }

       @Override
       public <T> T getLocal(Object o) {
         return null;
       }

       @Override
       public void putLocal(Object o, Object o1) {

       }

       @Override
       public boolean removeLocal(Object o) {
         return false;
       }

       @Override
       public Vertx owner() {
         return null;
       }

       @Override
       public int getInstanceCount() {
         return 0;
       }

       @Override
       public Context exceptionHandler(
         @Nullable Handler<Throwable> handler) {
         return null;
       }

       @Override
       public @Nullable Handler<Throwable> exceptionHandler() {
         return null;
       }
     };
    SAML2Client saml2Client = SamlClientLoader
      .configureSaml2Client(okaiUrl, tenantId, keystorePassword, privateKeyPassword, idpUrlResource,
        keystoreResource, samlBinding, idpMetadata, vertxContext);
    Assert.assertNotNull(saml2Client);
  }
}
