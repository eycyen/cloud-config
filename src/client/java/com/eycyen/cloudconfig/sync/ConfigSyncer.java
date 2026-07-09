package com.eycyen.cloudconfig.sync;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class ConfigSyncer {

    private final Drive driveService;
    private final String fileName = "skyhanni.json";
    private final java.io.File localFile;

    public ConfigSyncer(Drive driveService) {
        this.driveService = driveService;
        this.localFile = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve(fileName).toFile();
    }

    public String getFileId() throws Exception {
        FileList result = driveService.files().list()
                .setQ("name='" + fileName + "' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")
                .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        return null;
    }

    public void upload() throws Exception {
        if (!localFile.exists()) {
            throw new RuntimeException("Config file not found: " + localFile.getAbsolutePath());
        }

        String fileId = getFileId();
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        
        FileContent mediaContent = new FileContent("application/json", localFile);

        if (fileId == null) {
            driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();
        } else {
            driveService.files().update(fileId, fileMetadata, mediaContent).execute();
        }
    }

    public void download() throws Exception {
        String fileId = getFileId();
        
        if (fileId == null) {
            return;
        }

        if (!localFile.getParentFile().exists()) {
            localFile.getParentFile().mkdirs();
        }

        try (OutputStream outputStream = new FileOutputStream(localFile)) {
            driveService.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream);
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
        long localTime = localFile.lastModified();

        if (driveTime > localTime) {
            download();
        } else if (localTime > driveTime) {
            upload();
        }
    }
}