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
    private static final String APPLICATION_NAME = "Cloud Config Syncer";

    private File getTokenDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cloudconfig/tokens").toFile();
    }

    public Drive getDriveService(boolean allowBrowser, java.util.function.Consumer<String> browserUrlCallback, java.util.function.Consumer<String> progressCallback) throws Exception {
        if (progressCallback != null) progressCallback.accept("Initializing HTTP Transport...");
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        if (progressCallback != null) progressCallback.accept("Loading client secrets...");
        InputStream in = GoogleDriveManager.class.getResourceAsStream("/client_secret.json");
        if (in == null) {
            throw new RuntimeException("Resource not found: client_secret.json");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        if (progressCallback != null) progressCallback.accept("Building authorization flow...");
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(getTokenDirectory()))
                .setAccessType("offline")
                .build();

        Credential credential;
        if (allowBrowser) {
            if (progressCallback != null) progressCallback.accept("Starting local server receiver...");
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp.Browser browser = url -> {
                if (progressCallback != null) progressCallback.accept("Browser lambda executed!");
                    if (browserUrlCallback != null) {
                        browserUrlCallback.accept(url);
                    }
                    net.minecraft.client.Minecraft.getInstance().execute(() -> {

                        // 2. Try OS-specific native commands first because Minecraft's Util swallows exceptions silently
                        try {
                            String os = System.getProperty("os.name").toLowerCase();
                            if (os.contains("win")) {
                                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                            } else if (os.contains("mac")) {
                                Runtime.getRuntime().exec(new String[]{"open", url});
                            } else if (os.contains("nix") || os.contains("nux")) {
                                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                            } else {
                                net.minecraft.util.Util.getPlatform().openUri(new java.net.URI(url));
                            }
                        } catch (Exception e) {
                            // 3. Fallback to Minecraft's Util if the OS command fails
                            try {
                                net.minecraft.util.Util.getPlatform().openUri(new java.net.URI(url));
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                };
                if (progressCallback != null) progressCallback.accept("Authorizing (waiting for browser)...");
                credential = new AuthorizationCodeInstalledApp(flow, receiver, browser).authorize("user");
            } else {
                if (progressCallback != null) progressCallback.accept("Loading existing credential...");
                credential = flow.loadCredential("user");
                if (credential == null) {
                    if (progressCallback != null) progressCallback.accept("No credential found.");
                    return null;
                }
            }

        if (progressCallback != null) progressCallback.accept("Building Drive service...");
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}