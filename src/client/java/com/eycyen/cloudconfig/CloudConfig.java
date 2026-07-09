package com.eycyen.cloudconfig;

import com.eycyen.cloudconfig.auth.GoogleDriveManager;
import com.eycyen.cloudconfig.sync.ConfigSyncer;
import com.google.api.services.drive.Drive;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public class CloudConfig implements ClientModInitializer {

    public static Drive driveService;
    public static ConfigSyncer configSyncer;

    @Override
    public void onInitializeClient() {
        CompletableFuture.runAsync(() -> {
            try {
                GoogleDriveManager manager = new GoogleDriveManager();
                driveService = manager.getDriveService(false);

                if (driveService != null) {
                    configSyncer = new ConfigSyncer(driveService);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("cloudsync")
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("upload")
                    .executes(context -> {
                        CompletableFuture.runAsync(() -> {
                            try {
                                if (configSyncer == null) {
                                    context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§e[CloudConfig] Authenticating with Google Drive...")));
                                    GoogleDriveManager manager = new GoogleDriveManager();
                                    driveService = manager.getDriveService(true);
                                    if (driveService != null) {
                                        configSyncer = new ConfigSyncer(driveService);
                                    } else {
                                        context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§c[CloudConfig] Authentication failed or cancelled.")));
                                        return;
                                    }
                                }
                                context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§e[CloudConfig] Uploading to Drive...")));
                                configSyncer.upload();
                                context.getSource().getClient().execute(() ->
                                    context.getSource().sendFeedback(Component.literal("§a[CloudConfig] Upload successful!"))
                                );
                            } catch (Exception e) {
                                final String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                context.getSource().getClient().execute(() ->
                                    context.getSource().sendFeedback(Component.literal("§c[CloudConfig] Upload failed! Error: " + errMsg))
                                );
                                e.printStackTrace();
                            }
                        });
                        return 1;
                    })
                )
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("download")
                    .executes(context -> {
                        CompletableFuture.runAsync(() -> {
                            try {
                                if (configSyncer == null) {
                                    context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§e[CloudConfig] Authenticating with Google Drive...")));
                                    GoogleDriveManager manager = new GoogleDriveManager();
                                    driveService = manager.getDriveService(true);
                                    if (driveService != null) {
                                        configSyncer = new ConfigSyncer(driveService);
                                    } else {
                                        context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§c[CloudConfig] Authentication failed or cancelled.")));
                                        return;
                                    }
                                }
                                context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§e[CloudConfig] Downloading from Drive...")));
                                configSyncer.download();
                                context.getSource().getClient().execute(() ->
                                    context.getSource().sendFeedback(Component.literal("§a[CloudConfig] Download successful!"))
                                );
                            } catch (Exception e) {
                                final String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                context.getSource().getClient().execute(() ->
                                    context.getSource().sendFeedback(Component.literal("§c[CloudConfig] Download failed! Error: " + errMsg))
                                );
                                e.printStackTrace();
                            }
                        });
                        return 1;
                    })
                )
            );
        });
    }
}