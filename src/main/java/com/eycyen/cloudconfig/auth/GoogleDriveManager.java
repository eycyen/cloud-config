package com.eycyen.cloudconfig.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import java.io.InputStream;
import java.io.InputStreamReader;

public class GoogleDriveManager {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public void getDriveService() {
        try {
            InputStream in = GoogleDriveManager.class.getResourceAsStream("/client_secret.json");

            if (in == null) {
                throw new RuntimeException("Resource not found: client_secret.json");
            }

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            System.out.println("Successfully read! Client ID: " + clientSecrets.getDetails().getClientId());
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
