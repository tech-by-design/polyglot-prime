package org.techbd.service.http.filter;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@ConfigurationProperties(prefix = "org.techbd.service.http.filter.sensitive")
@Service
public class GitHubUserService {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitHubUserService.class);

  // @Value("${ORG_TECHBD_SERVICE_HTTP_FILTER_SENSITIVE_REPO_URL}")
  // private String url;
  private String url = System.getenv("ORG_TECHBD_SERVICE_HTTP_FILTER_SENSITIVE_REPO_URL");

  // @Value("${ORG_TECHBD_SERVICE_HTTP_FILTER_SENSITIVE_TOKEN}")
  // private String token;
  private String token = System.getenv("ORG_TECHBD_SERVICE_HTTP_FILTER_SENSITIVE_TOKEN");

  public Users getUserList() throws IOException {
    String downloadUrl;
    String ymlData;

    OkHttpClient client = new OkHttpClient.Builder()
        .followRedirects(true)
        .build();

    Request request = new Request.Builder()
        .url(url)
        .header("Authorization", "token " + token)
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        JSONObject jsonResponse = new JSONObject(response.body().string());
        downloadUrl = jsonResponse.getString("download_url");
      } else {
        LOGGER.error("Unexpected code {}", response);
        return null;
      }
    } catch (IOException e) {
      LOGGER.error("Error executing request: ", e);
      return null;
    }
    Request newRequest = new Request.Builder()
        .url(downloadUrl)
        .header("Authorization", "token " + token)
        .build();
    try (Response response = client.newCall(newRequest).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        ymlData = response.body().string();
      } else {
        LOGGER.error("Unexpected code {}", response);
        return null;
      }
    } catch (IOException e) {
      LOGGER.error("Error executing request: ", e);
      return null;
    }

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Users users = mapper.readValue(ymlData, Users.class);
    System.out.println("");
    return users;
  }
}
