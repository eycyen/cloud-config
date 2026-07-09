package com.eycyen.cloudconfig.auth;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public class GoogleDriveManager {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String TOKENS_DIRECTORY_PATH = "config/cloudconfig/tokens";
    private static final String APPLICATION_NAME = "Cloud Config Syncer";

    public boolean hasToken() {
        File tokenDir = new File(TOKENS_DIRECTORY_PATH);
        if (tokenDir.exists() && tokenDir.isDirectory()) {
            File[] files = tokenDir.listFiles();
            return files != null && files.length > 0;
        }
        return false;
    }

    public Drive getDriveService(boolean allowBrowser) {
        if (!allowBrowser && !hasToken()) {
            return null;
        }
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

            InputStream in = GoogleDriveManager.class.getResourceAsStream("/client_secret.json");
            if (in == null) {
                throw new RuntimeException("Resource not found: client_secret.json");
            }
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            
            com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp.Browser browser = url -> {
                try {
                    net.minecraft.util.Util.getPlatform().openUri(new java.net.URI(url));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            Credential credential = new AuthorizationCodeInstalledApp(flow, receiver, browser).authorize("user");

            return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}