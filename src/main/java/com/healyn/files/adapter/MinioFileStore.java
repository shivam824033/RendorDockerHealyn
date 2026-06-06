package com.healyn.files.adapter;

import com.healyn.files.config.HealynS3Properties;
import com.healyn.files.port.FileStorePort;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class MinioFileStore implements FileStorePort {

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final MinioClient client;
    private final MinioClient presignClient;
    private final String bucket;

    public MinioFileStore(MinioClient client,
                          @Qualifier("minioPresignClient") MinioClient presignClient,
                          HealynS3Properties props) {
        this.client = client;
        this.presignClient = presignClient;
        this.bucket = props.bucket();
    }

    @Override
    public String presignPut(String key, String contentType, Duration ttl) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) ttl.toSeconds())
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("Failed to presign PUT for " + key, e);
        }
    }

    @Override
    public String presignGet(String key, String downloadFilename, Duration ttl) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry((int) ttl.toSeconds())
                    .extraQueryParams(Map.of(
                            "response-content-disposition",
                            "attachment; filename=\"" + downloadFilename + "\""))
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("Failed to presign GET for " + key, e);
        }
    }

    @Override
    public Optional<Long> objectSize(String key) {
        try {
            var stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return Optional.of(stat.size());
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new FileStorageException("Failed to stat " + key, e);
        } catch (Exception e) {
            throw new FileStorageException("Failed to stat " + key, e);
        }
    }

    @Override
    public byte[] read(String key, long maxBytes) {
        try (InputStream is = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .offset(0L)
                .length(maxBytes)
                .build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new FileStorageException("Failed to read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("Failed to delete " + key, e);
        }
    }
}
