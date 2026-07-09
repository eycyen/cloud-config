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
                driveService = manager.getDriveService(false, null, null);

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
                    configSyncer.upload(null);
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
                                    driveService = manager.getDriveService(true, url -> {
                                        context.getSource().getClient().execute(() -> {
                                            context.getSource().sendFeedback(Component.literal("§b[CloudConfig] Browser didn't open? Click this link to log in:"));
                                            context.getSource().sendFeedback(Component.literal("§n" + url));
                                        });
                                    }, progress -> {
                                        context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§7[CloudConfig] " + progress)));
                                    });
                                    if (driveService != null) {
                                        configSyncer = new ConfigSyncer(driveService);
                                    } else {
                                        context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§c[CloudConfig] Authentication failed or cancelled.")));
                                        return;
                                    }
                                }
                                java.util.function.Consumer<String> progressBar = msg -> {
                                    context.getSource().getClient().execute(() -> {
                                        net.minecraft.client.player.LocalPlayer player = context.getSource().getClient().player;
                                        if (player != null) {
                                        player.sendSystemMessage(Component.literal(msg));
                                        }
                                    });
                                };
                                configSyncer.upload(progressBar);
                                context.getSource().getClient().execute(() -> {
                                    context.getSource().sendFeedback(Component.literal("§a[CloudConfig] Upload successful!"));
                                    net.minecraft.client.player.LocalPlayer player = context.getSource().getClient().player;
                                    if (player != null) {
                                        player.sendSystemMessage(Component.literal("§a[CloudConfig] Upload complete!"));
                                    }
                                });
                            } catch (Throwable e) {
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
                                    driveService = manager.getDriveService(true, url -> {
                                        context.getSource().getClient().execute(() -> {
                                            context.getSource().sendFeedback(Component.literal("§b[CloudConfig] Browser didn't open? Click this link to log in:"));
                                            context.getSource().sendFeedback(Component.literal("§n" + url));
                                        });
                                    }, progress -> {
                                        context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§7[CloudConfig] " + progress)));
                                    });
                                    if (driveService != null) {
                                        configSyncer = new ConfigSyncer(driveService);
                                    } else {
                                        context.getSource().getClient().execute(() -> context.getSource().sendFeedback(Component.literal("§c[CloudConfig] Authentication failed or cancelled.")));
                                        return;
                                    }
                                }
                                java.util.function.Consumer<String> progressBar = msg -> {
                                    context.getSource().getClient().execute(() -> {
                                        net.minecraft.client.player.LocalPlayer player = context.getSource().getClient().player;
                                        if (player != null) {
                                        player.sendSystemMessage(Component.literal(msg));
                                        }
                                    });
                                };
                                configSyncer.download(progressBar);
                                context.getSource().getClient().execute(() -> {
                                    context.getSource().sendFeedback(Component.literal("§a[CloudConfig] Download successful!"));
                                    net.minecraft.client.player.LocalPlayer player = context.getSource().getClient().player;
                                    if (player != null) {
                                        player.sendSystemMessage(Component.literal("§a[CloudConfig] Download complete!"));
                                    }
                                });
                            } catch (Throwable e) {
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