package com.eycyen.cloudconfig.sync;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ConfigSyncer {

    private final Drive driveService;
    private final String driveFileName = "minecraft-config-backup.zip";
    private final Path configDir;
    private final Path tempZipFile;

    public ConfigSyncer(Drive driveService) {
        this.driveService = driveService;
        this.configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        this.tempZipFile = configDir.resolve("../.cloudconfig-temp.zip").normalize();
    }

    public String getFileId() throws Exception {
        FileList result = driveService.files().list()
                .setQ("name='" + driveFileName + "' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")
                .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        return null;
    }

    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Skip the temp zip file itself and cloudconfig token directory
                    String relativePath = sourceDir.relativize(file).toString();
                    if (relativePath.contains(".cloudconfig-temp") || relativePath.startsWith("cloudconfig")) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        zos.putNextEntry(new ZipEntry(relativePath));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        // Skip locked files (common on Windows when mods hold file locks)
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String relativePath = sourceDir.relativize(dir).toString();
                    if (relativePath.startsWith("cloudconfig")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!relativePath.isEmpty()) {
                        zos.putNextEntry(new ZipEntry(relativePath + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void unzipToDirectory(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = targetDir.resolve(entry.getName()).normalize();

                // Security check: prevent zip slip
                if (!targetPath.startsWith(targetDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (OutputStream os = new FileOutputStream(targetPath.toFile())) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public void upload() throws Exception {
        if (!Files.exists(configDir) || !Files.isDirectory(configDir)) {
            throw new RuntimeException("Config directory not found: " + configDir.toAbsolutePath());
        }

        // Zip the entire config directory
        zipDirectory(configDir, tempZipFile);

        try {
            String fileId = getFileId();
            File fileMetadata = new File();
            fileMetadata.setName(driveFileName);

            FileContent mediaContent = new FileContent("application/zip", tempZipFile.toFile());

            if (fileId == null) {
                driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();
            } else {
                driveService.files().update(fileId, fileMetadata, mediaContent).execute();
            }
        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempZipFile);
        }
    }

    public void download() throws Exception {
        String fileId = getFileId();

        if (fileId == null) {
            throw new RuntimeException("No backup found on Google Drive.");
        }

        try {
            // Download zip to temp file
            try (OutputStream outputStream = new FileOutputStream(tempZipFile.toFile())) {
                driveService.files().get(fileId)
                        .executeMediaAndDownloadTo(outputStream);
            }

            // Extract to config directory
            unzipToDirectory(tempZipFile, configDir);
        } finally {
            // Clean up temp file
            Files.deleteIfExists(tempZipFile);
        }
    }

    public void syncOnStartup() throws Exception {
        String fileId = getFileId();

        if (fileId == null) {
            upload();
            return;
        }

        File driveFile = driveService.files().get(fileId).setFields("modifiedTime").execute();
        long driveTime = driveFile.getModifiedTime().getValue();
        long localTime = Files.getLastModifiedTime(configDir).toMillis();

        if (driveTime > localTime) {
            download();
        } else if (localTime > driveTime) {
            upload();
        }
    }
}