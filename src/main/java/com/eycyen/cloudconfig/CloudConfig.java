package com.eycyen.cloudconfig;

import com.eycyen.cloudconfig.auth.GoogleDriveManager;
import com.eycyen.cloudconfig.sync.ConfigSyncer;
import com.google.api.services.drive.Drive;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.util.concurrent.CompletableFuture;

public class CloudConfig implements ModInitializer {

    public static Drive driveService;
    public static ConfigSyncer configSyncer;

    @Override
    public void onInitialize() {
        CompletableFuture.runAsync(() -> {
            GoogleDriveManager manager = new GoogleDriveManager();
            driveService = manager.getDriveService();
            
            if (driveService != null) {
                configSyncer = new ConfigSyncer(driveService);
            }
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (configSyncer != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        configSyncer.syncOnStartup();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (configSyncer != null) {
                try {
                    configSyncer.upload();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}