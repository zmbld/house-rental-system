package com.homestay.controller;

import com.homestay.common.Result;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传（房产证、房屋图片等）
 */
@Controller
public class FileController {

    /** 上传根目录，与 WebConfig 中 /upload/** 的资源映射保持一致 */
    private static final String UPLOAD_DIR = "D:/upload/homestay/";

    @PostMapping("/api/upload")
    @ResponseBody
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.lastIndexOf('.') >= 0) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)")) {
            return Result.error("仅支持 jpg/jpeg/png/gif/webp/bmp 图片格式");
        }
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.error("上传目录创建失败");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            file.transferTo(new File(dir, fileName));
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
        Map<String, String> data = new HashMap<>();
        data.put("url", "/upload/" + fileName);
        data.put("fileName", fileName);
        return Result.ok("上传成功", data);
    }
}
