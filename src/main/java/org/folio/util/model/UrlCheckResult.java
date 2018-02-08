package org.folio.util.model;

import java.util.Objects;

/**
 * @author rsass
 */
public class UrlCheckResult {

  private final Status status;
  private final String message;

  public UrlCheckResult(Status status, String message) {
    Objects.requireNonNull(status);
    Objects.requireNonNull(message);
    this.status = status;
    this.message = message;
  }

  public static UrlCheckResult emptySuccessResult() {
    return successResult("");
  }

  public static UrlCheckResult successResult(String message) {
    return new UrlCheckResult(Status.SUCCESS, message);
  }

  public static UrlCheckResult failResult(String message) {
    return new UrlCheckResult(Status.FAIL, message);
  }

  public static UrlCheckResult emptyFailResult() {
    return failResult("");
  }

  public boolean isSuccess() {
    return status.equals(Status.SUCCESS);
  }

  public Status getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public enum Status {
    SUCCESS, FAIL
  }
}
