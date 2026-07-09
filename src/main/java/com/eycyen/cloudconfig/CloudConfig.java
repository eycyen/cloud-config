package com.eycyen.cloudconfig;

import com.eycyen.cloudconfig.auth.GoogleDriveManager;
import com.google.api.services.drive.Drive;
import net.fabricmc.api.ModInitializer;

import java.util.concurrent.CompletableFuture;

public class CloudConfig implements ModInitializer {

    public static Drive driveService;

    @Override
    public void onInitialize() {
        CompletableFuture.runAsync(() -> {
            GoogleDriveManager manager = new GoogleDriveManager();
            driveService = manager.getDriveService();
            
            if (driveService != null) {
                System.out.println("Cloud Config: Drive API initialized successfully!");
            }
        });
    }
}