package com.sample.resources;

import static com.jayway.restassured.RestAssured.given;

import javax.annotation.PostConstruct;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.stereotype.Component;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.HttpClientConfig;
import com.jayway.restassured.internal.RestAssuredResponseImpl;
import com.sample.conf.TestLog;

@Component
@Produces(MediaType.APPLICATION_JSON)
@Path("/api")
public class RestResource {

  private static final String TEST_URL = "http://localhost:8082/api/test";

  @PostConstruct
  public void init() {
    // default connectionConfig does bad job that create a http client instance for every call and
    // results in CLOSE_WAIT connections (TOO BAD) and closeIdleConnectionsAfterEachResponse also
    // doesn't work well. SUCKS.
    // RestAssured.config = RestAssured.config();
    // .connectionConfig(new ConnectionConfig().closeIdleConnectionsAfterEachResponse());

    initWithHttpClientReuseAndConnPooling();
  }

  private void initWithHttpClientReuseAndConnPooling() {
    HttpClientConfig clientConfig = RestAssured.config().getHttpClientConfig();
    clientConfig = clientConfig.httpClientFactory(new HttpClientConfig.HttpClientFactory() {
      @Override
      public HttpClient createHttpClient() {
        /*
         * @SuppressWarnings("deprecation") HttpClient rv = new SystemDefaultHttpClient();
         * HttpParams httpParams = rv.getParams();
         * HttpConnectionParams.setConnectionTimeout(httpParams, 5 * 1000); // Wait 5s for a
         * connection HttpConnectionParams.setSoTimeout(httpParams, 60 * 1000); // Default session
         * is 60s return rv;
         */

        // following code builds InternalHttpClient which doesn't work rest assured because rest
        // assured RequestSpecificationImpl sticks to AbstractHttpClient, might have to build custom
        // client on AbstractHttpClient that can use a shared PoolingHttpClientConnectionManager

        PoolingHttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(2);
        connectionManager.setMaxTotal(2);

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000)
            .setConnectionRequestTimeout(10000).setSocketTimeout(10000).build();

        CloseableHttpClient httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig).build();

        return httpClient;

      }
    });
    // This is necessary to ensure, that the client is reused.
    clientConfig = clientConfig.reuseHttpClientInstance();
    RestAssured.config = RestAssured.config().httpClient(clientConfig);
  }

  /**
   * Call the endpoint and monitor tcp view.
   */
  @GET
  @Path("test")
  public void testConnSocketLeak() throws InterruptedException {
    TestLog.log("started endpoint calls");
    for (int j = 0; j < 10; j++) {
      for (int k = 0; k < 300; k++) {
        RestAssuredResponseImpl text = (RestAssuredResponseImpl) given()
            .contentType("application/json").accept("application/json").when().get(TEST_URL);
        text.asString();
      }
      TestLog.log("finished iteration " + j);
    }
    TestLog.log("finished endpoint calls");
    int sleepInSecs = 1000 * 60 * 5;
    Thread.sleep(sleepInSecs);
    TestLog.log("completely done");
  }

}
