package com.eycyen.cloudconfig;

import com.eycyen.cloudconfig.auth.GoogleDriveManager;
import com.eycyen.cloudconfig.sync.ConfigSyncer;
import com.google.api.services.drive.Drive;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

public class CloudConfig implements ClientModInitializer {

    public static Drive driveService;
    public static ConfigSyncer configSyncer;

    @Override
    public void onInitializeClient() {
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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("cloudsync")
                .then(ClientCommandManager.literal("upload")
                    .executes(context -> {
                        if (configSyncer != null) {
                            context.getSource().sendFeedback(Text.literal("§e[CloudConfig] Uploading to Drive..."));
                            CompletableFuture.runAsync(() -> {
                                try {
                                    configSyncer.upload();
                                    context.getSource().getClient().execute(() ->
                                        context.getSource().sendFeedback(Text.literal("§a[CloudConfig] Upload successful!"))
                                    );
                                } catch (Exception e) {
                                    context.getSource().getClient().execute(() ->
                                        context.getSource().sendFeedback(Text.literal("§c[CloudConfig] Upload failed!"))
                                    );
                                    e.printStackTrace();
                                }
                            });
                        }
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("download")
                    .executes(context -> {
                        if (configSyncer != null) {
                            context.getSource().sendFeedback(Text.literal("§e[CloudConfig] Downloading from Drive..."));
                            CompletableFuture.runAsync(() -> {
                                try {
                                    configSyncer.download();
                                    context.getSource().getClient().execute(() ->
                                        context.getSource().sendFeedback(Text.literal("§a[CloudConfig] Download successful!"))
                                    );
                                } catch (Exception e) {
                                    context.getSource().getClient().execute(() ->
                                        context.getSource().sendFeedback(Text.literal("§c[CloudConfig] Download failed!"))
                                    );
                                    e.printStackTrace();
                                }
                            });
                        }
                        return 1;
                    })
                )
            );
        });
    }
}