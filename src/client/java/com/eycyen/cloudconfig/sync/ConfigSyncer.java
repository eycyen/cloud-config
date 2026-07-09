package com.eycyen.cloudconfig.sync;

import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
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

    private int countFiles(Path sourceDir) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String rel = sourceDir.relativize(file).toString();
                if (!rel.contains(".cloudconfig-temp") && !rel.startsWith("cloudconfig")) {
                    count.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String rel = sourceDir.relativize(dir).toString();
                if (rel.startsWith("cloudconfig")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }

    private String buildProgressBar(double fraction) {
        int barLength = 20;
        int filled = (int) (fraction * barLength);
        StringBuilder bar = new StringBuilder("\u00a7a");
        for (int i = 0; i < filled; i++) bar.append("|");
        bar.append("\u00a77");
        for (int i = filled; i < barLength; i++) bar.append("|");
        bar.append(" \u00a7f").append((int) (fraction * 100)).append("%");
        return bar.toString();
    }

    private void zipDirectory(Path sourceDir, Path zipFile, Consumer<String> progress) throws IOException {
        int totalFiles = countFiles(sourceDir);
        AtomicInteger processed = new AtomicInteger(0);

        if (progress != null) progress.accept("\u00a7e[CloudConfig] Compressing config... " + buildProgressBar(0));

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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
                    int done = processed.incrementAndGet();
                    if (progress != null && (done % 10 == 0 || done == totalFiles)) {
                        double fraction = totalFiles > 0 ? (double) done / totalFiles : 1.0;
                        progress.accept("\u00a7e[CloudConfig] Compressing config... " + buildProgressBar(fraction));
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

    private void unzipToDirectory(Path zipFile, Path targetDir, Consumer<String> progress) throws IOException {
        // First pass: count entries
        int totalEntries = 0;
        try (ZipInputStream countStream = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            while (countStream.getNextEntry() != null) {
                totalEntries++;
                countStream.closeEntry();
            }
        }

        if (progress != null) progress.accept("\u00a7e[CloudConfig] Extracting config... " + buildProgressBar(0));

        int processed = 0;
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

                processed++;
                if (progress != null && (processed % 10 == 0 || processed == totalEntries)) {
                    double fraction = totalEntries > 0 ? (double) processed / totalEntries : 1.0;
                    progress.accept("\u00a7e[CloudConfig] Extracting config... " + buildProgressBar(fraction));
                }
            }
        }
    }

    public void upload(Consumer<String> progress) throws Exception {
        if (!Files.exists(configDir) || !Files.isDirectory(configDir)) {
            throw new RuntimeException("Config directory not found: " + configDir.toAbsolutePath());
        }

        // Zip the entire config directory
        zipDirectory(configDir, tempZipFile, progress);

        try {
            String fileId = getFileId();
            File fileMetadata = new File();
            fileMetadata.setName(driveFileName);

            FileContent mediaContent = new FileContent("application/zip", tempZipFile.toFile());
            long fileSize = tempZipFile.toFile().length();
            String fileSizeMB = String.format("%.1f MB", fileSize / (1024.0 * 1024.0));

            if (progress != null) progress.accept("\u00a7e[CloudConfig] Uploading " + fileSizeMB + "... " + buildProgressBar(0));

            if (fileId == null) {
                var request = driveService.files().create(fileMetadata, mediaContent).setFields("id");
                request.getMediaHttpUploader().setChunkSize(2 * 1024 * 1024); // 2MB chunks
                request.getMediaHttpUploader().setProgressListener(uploader -> {
                    if (progress != null) {
                        double fraction = uploader.getProgress();
                        progress.accept("\u00a7e[CloudConfig] Uploading " + fileSizeMB + "... " + buildProgressBar(fraction));
                    }
                });
                request.execute();
            } else {
                var request = driveService.files().update(fileId, fileMetadata, mediaContent);
                request.getMediaHttpUploader().setChunkSize(2 * 1024 * 1024);
                request.getMediaHttpUploader().setProgressListener(uploader -> {
                    if (progress != null) {
                        double fraction = uploader.getProgress();
                        progress.accept("\u00a7e[CloudConfig] Uploading " + fileSizeMB + "... " + buildProgressBar(fraction));
                    }
                });
                request.execute();
            }
        } finally {
            Files.deleteIfExists(tempZipFile);
        }
    }

    public void download(Consumer<String> progress) throws Exception {
        String fileId = getFileId();

        if (fileId == null) {
            throw new RuntimeException("No backup found on Google Drive.");
        }

        try {
            // Get file size for progress
            File driveFile = driveService.files().get(fileId).setFields("size").execute();
            long fileSize = driveFile.getSize() != null ? driveFile.getSize() : 0;
            String fileSizeMB = String.format("%.1f MB", fileSize / (1024.0 * 1024.0));

            if (progress != null) progress.accept("\u00a7e[CloudConfig] Downloading " + fileSizeMB + "... " + buildProgressBar(0));

            // Download zip to temp file
            var request = driveService.files().get(fileId);
            request.getMediaHttpDownloader().setChunkSize(2 * 1024 * 1024);
            request.getMediaHttpDownloader().setProgressListener(downloader -> {
                if (progress != null) {
                    double fraction = downloader.getProgress();
                    progress.accept("\u00a7e[CloudConfig] Downloading " + fileSizeMB + "... " + buildProgressBar(fraction));
                }
            });

            try (OutputStream outputStream = new FileOutputStream(tempZipFile.toFile())) {
                request.executeMediaAndDownloadTo(outputStream);
            }

            // Extract to config directory
            unzipToDirectory(tempZipFile, configDir, progress);
        } finally {
            Files.deleteIfExists(tempZipFile);
        }
    }

    public void syncOnStartup() throws Exception {
        String fileId = getFileId();

        if (fileId == null) {
            upload(null);
            return;
        }

        File driveFile = driveService.files().get(fileId).setFields("modifiedTime").execute();
        long driveTime = driveFile.getModifiedTime().getValue();
        long localTime = Files.getLastModifiedTime(configDir).toMillis();

        if (driveTime > localTime) {
            download(null);
        } else if (localTime > driveTime) {
            upload(null);
        }
    }
}