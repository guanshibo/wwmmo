package au.com.codeka.warworlds.server.admin;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.wire.Message;
import com.squareup.wire.WireTypeAdapterFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Matcher;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import au.com.codeka.warworlds.common.Log;

/**
 * This is the base class for the game's request handlers. It handles some common tasks such as
 * extracting protocol buffers from the request body, and so on.
 */
public class RequestHandler {
  private final Log log = new Log("RequestHandler");
  private HttpServletRequest request;
  private HttpServletResponse response;
  private Matcher routeMatcher;
  private Session session;
  private String extraOption;

  protected String getUrlParameter(String name) {
    try {
      return routeMatcher.group(name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  protected String getRealm() {
    return getUrlParameter("realm");
  }

  /** Gets the "extra" option that was passed in the route configuration. */
  @Nullable
  protected String getExtraOption() {
    return extraOption;
  }

  public void handle(Matcher routeMatcher, String extraOption, Session session,
      HttpServletRequest request, HttpServletResponse response) throws RequestException {
    this.request = request;
    this.response = response;
    this.routeMatcher = routeMatcher;
    this.extraOption = extraOption;
    this.session = session;

    // start off with status 200, but the handler might change it
    this.response.setStatus(200);

    try {
      onBeforeHandle();
      if (request.getMethod().equals("GET")) {
        get();
      } else if (request.getMethod().equals("POST")) {
        post();
      } else if (request.getMethod().equals("PUT")) {
        put();
      } else if (request.getMethod().equals("DELETE")) {
        delete();
      } else {
        throw new RequestException(501);
      }
    } catch (RequestException e) {
      handleException(e);
    }
  }

  protected void handleException(RequestException e) throws RequestException {
    throw e;
  }

  /**
   * This is called before the get(), put(), etc methods but after the request
   * is set up, ready to go.
   */
  protected void onBeforeHandle() {
  }

  protected void get() throws RequestException {
    throw new RequestException(501);
  }

  protected void put() throws RequestException {
    throw new RequestException(501);
  }

  protected void post() throws RequestException {
    throw new RequestException(501);
  }

  protected void delete() throws RequestException {
    throw new RequestException(501);
  }

  protected void setCacheTime(float hours) {
    setCacheTime(hours, null);
  }

  /**
   * Sets the required headers so that the client will know this response can be cached for the
   * given number of hours. The default response includes no caching headers.
   *
   * @param hours Time, in hours, to cache this response.
   * @param etag An optional value to include in the ETag header. This can be any string at all,
   *             and we will hash and base-64 encode it for you.
   */
  protected void setCacheTime(float hours, @Nullable String etag) {
    response.setHeader("Cache-Control",
        String.format(Locale.US, "private, max-age=%d", (int)(hours * 3600)));
    if (etag != null) {
      etag = BaseEncoding.base64().encode(
          Hashing.sha1().hashString(etag, Charset.defaultCharset()).asBytes());
      response.setHeader("ETag", String.format("\"%s\"", etag));
    }
  }

  protected void setResponseText(String text) {
    response.setContentType("text/plain");
    response.setCharacterEncoding("utf-8");
    try {
      response.getWriter().write(text);
    } catch (IOException e) {
      // Ignore?
    }
  }

  protected void setResponseJson(Message<?, ?> pb) {
    response.setContentType("application/json");
    response.setCharacterEncoding("utf-8");
    try {
      PrintWriter writer = response.getWriter();
      Gson gson = new GsonBuilder()
          .registerTypeAdapterFactory(new WireTypeAdapterFactory())
          .disableHtmlEscaping()
          .create();
      writer.write(gson.toJson(pb));
      writer.flush();
    } catch (IOException e) {
      // Ignore.
    }
  }

  protected void setResponseGson(Object obj) {
    response.setContentType("application/json");
    response.setCharacterEncoding("utf-8");
    try {
      PrintWriter writer = response.getWriter();
      Gson gson = new GsonBuilder()
          .disableHtmlEscaping()
          .create();
      writer.write(gson.toJson(obj));
      writer.flush();
    } catch (IOException e) {
      // Ignore.
    }
  }

  protected void redirect(String url) {
    response.setStatus(302);
    response.addHeader("Location", url);
  }

  protected HttpServletRequest getRequest() {
    return request;
  }

  protected HttpServletResponse getResponse() {
    return response;
  }

  protected String getRequestUrl() {
    URI requestURI;
    try {
      requestURI = new URI(request.getRequestURL().toString());
    } catch (URISyntaxException e) {
      return null; // should never happen!
    }

    // TODO(dean): is hard-coding the https part for game.war-worlds.com the best way? no...
    if (requestURI.getHost().equals("game.war-worlds.com")) {
      return "https://game.war-worlds.com" + requestURI.getPath();
    } else {
      return requestURI.toString();
    }
  }

  protected Session getSession() throws RequestException {
    if (session == null) {
      throw new RequestException(403);
    }

    return session;
  }

  @Nullable
  protected Session getSessionNoError() {
    return session;
  }

  /**
   * Checks whether the current user is in the given role. If the user is not an admin, then they
   * are -- by definition -- not in any roles.
   *//*
  protected boolean isInRole(BackendUser.Role role) throws RequestException {
    if (session == null || !session.isAdmin()) {
      return false;
    }

    BackendUser backendUser = new AdminController().getBackendUser(session.getActualEmail());
    if (backendUser == null) {
      // should  be impossible if it's really an admin user...
      throw new RequestException(500, "This is impossible.");
    }

    return backendUser.isInRole(role);
  }*/

  @Nullable
  private <T> T getRequestJson(Class<T> protoType) {
    String json;
    try {
      Scanner scanner = new Scanner(request.getInputStream(), request.getCharacterEncoding())
          .useDelimiter("\\A");
      json = scanner.hasNext() ? scanner.next() : "";
    } catch (IOException e) {
      return null;
    }

    try {
      Gson gson = new GsonBuilder()
          .registerTypeAdapterFactory(new WireTypeAdapterFactory())
          .disableHtmlEscaping()
          .create();
      return gson.fromJson(json, protoType);
    } catch (Exception e) {
      return null;
    }
  }

  /** Get details about the given request as a string (for debugging). */
  private String getRequestDebugString(HttpServletRequest request) {
    return request.getRequestURI()
        + "\nX-Real-IP: " + request.getHeader("X-Real-IP")
        + "\nUser-Agent: " + request.getHeader("User-Agent")
        + "\n";
  }
}
