package com.hongik.grad.notifications.client;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Initializes the Firebase Admin SDK from a service-account credentials file. When the credentials
 * path is unset or unreadable, no {@link FirebaseMessaging} bean is registered (returns {@code
 * null}), so the application boots normally and {@link
 * com.hongik.grad.notifications.application.FcmSender} degrades to a no-op.
 */
@Configuration
public class FirebaseInitializer {

  private static final Logger log = LoggerFactory.getLogger(FirebaseInitializer.class);
  private static final String APP_NAME = "hongik-grad";

  private final String credentialsPath;
  private final String projectId;

  public FirebaseInitializer(
      @Value("${firebase.credentials-path:}") String credentialsPath,
      @Value("${firebase.project-id:}") String projectId) {
    this.credentialsPath = credentialsPath;
    this.projectId = projectId;
  }

  @Bean
  public FirebaseMessaging firebaseMessaging() {
    if (!StringUtils.hasText(credentialsPath)) {
      log.warn("FIREBASE_CREDENTIALS_PATH not configured — FCM push disabled (no-op).");
      return null;
    }
    Path path = Path.of(credentialsPath);
    if (!Files.isReadable(path)) {
      log.error(
          "Firebase credentials file not readable at {} — FCM push disabled (no-op).",
          credentialsPath);
      return null;
    }
    try (InputStream in = Files.newInputStream(path)) {
      FirebaseOptions.Builder builder =
          FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in));
      if (StringUtils.hasText(projectId)) {
        builder.setProjectId(projectId);
      }
      FirebaseApp app = initApp(builder.build());
      log.info("Firebase Admin SDK initialized (project_id={}).", projectId);
      return FirebaseMessaging.getInstance(app);
    } catch (Exception ex) {
      log.error("Failed to initialize Firebase Admin SDK — FCM push disabled (no-op).", ex);
      return null;
    }
  }

  private FirebaseApp initApp(FirebaseOptions options) {
    return FirebaseApp.getApps().stream()
        .filter(a -> APP_NAME.equals(a.getName()))
        .findFirst()
        .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
  }
}
