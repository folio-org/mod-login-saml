package org.folio.session;

import io.vertx.ext.web.Session;

import java.util.Map;
import java.util.function.Function;

/**
 * @author rsass
 */
public class NoopSession implements Session {
  @Override
  public Session regenerateId() {
    return this;
  }

  @Override
  public String id() {
    return "";
  }

  @Override
  public Session put(String key, Object obj) {
    return this;
  }

  @Override
  public Session putIfAbsent(String s, Object o) {
    return this;
  }

  @Override
  public Session computeIfAbsent(String s, Function<String, Object> function) {
    return this;
  }

  @Override
  public <T> T get(String key) {
    return null;
  }

  @Override
  public <T> T remove(String key) {
    return null;
  }

  @Override
  public Map<String, Object> data() {
    return null;
  }

  @Override
  public long lastAccessed() {
    return 0;
  }

  @Override
  public void destroy() {

  }

  @Override
  public boolean isDestroyed() {
    return false;
  }

  @Override
  public boolean isRegenerated() {
    return false;
  }

  @Override
  public String oldId() {
    return "";
  }

  @Override
  public long timeout() {
    return 0;
  }

  @Override
  public void setAccessed() {

  }

  @Override
  public boolean isEmpty() {
    return false;
  }
}
