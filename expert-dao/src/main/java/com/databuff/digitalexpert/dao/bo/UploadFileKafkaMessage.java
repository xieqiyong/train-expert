package com.databuff.digitalexpert.dao.bo;

import lombok.Data;

@Data
public class UploadFileKafkaMessage {

    /**
     * 原始文件名。
     */
    private String originalFileName;

    /**
     * 存储后的文件名。
     */
    private String storageFileName;

    /**
     * 文件类型。
     */
    private String fileType;

    /**
     * 文件大小，单位字节。
     */
    private Long fileSize;

    /**
     * 上传文件在磁盘上的完整路径。
     */
    private String storagePath;

    /**
     * 上传文件访问地址。
     */
    private String accessUrl;

    /**
     * 上传时间戳。
     */
    private Long uploadTime;
}
