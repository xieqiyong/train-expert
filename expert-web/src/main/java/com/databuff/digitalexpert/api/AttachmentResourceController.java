package com.databuff.digitalexpert.api;

import com.databuff.digitalexpert.dao.dto.AttachmentResourceQueryRequest;
import com.databuff.digitalexpert.dao.dto.AttachmentResourceResponse;
import com.databuff.digitalexpert.dao.response.ApiResponse;
import com.databuff.digitalexpert.service.AttachmentResourceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentResourceController {

    @Autowired
    private AttachmentResourceService attachmentResourceService;

    @PostMapping("/list")
    public ApiResponse<List<AttachmentResourceResponse>> list(@RequestBody(required = false) AttachmentResourceQueryRequest request) {
        return ApiResponse.success(attachmentResourceService.listResources(
                request == null ? null : request.scope(),
                request == null ? null : request.status()
        ));
    }
}
