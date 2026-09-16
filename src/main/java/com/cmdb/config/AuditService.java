package com.cmdb.config;

import com.cmdb.entity.AssetChange;
import com.cmdb.entity.OperationAudit;
import com.cmdb.repo.AssetChangeRepository;
import com.cmdb.repo.OperationAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuditService {
    private final AssetChangeRepository assetChanges;
    private final OperationAuditRepository operations;

    public AuditService(AssetChangeRepository assetChanges, OperationAuditRepository operations) {
        this.assetChanges = assetChanges;
        this.operations = operations;
    }

    public void operation(HttpServletRequest request, String action, String resourceType,
                          Object resourceId, String resourceName, String result, String message) {
        OperationAudit audit = new OperationAudit();
        audit.setOperator(attribute(request, "username"));
        audit.setRole(attribute(request, "role"));
        audit.setAction(action);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        audit.setResourceName(limit(resourceName, 200));
        audit.setResult(result);
        audit.setMessage(limit(message, 1000));
        audit.setClientIp(clientIp(request));
        operations.save(audit);
    }

    public void assetChange(Long assetId, String assetName, String changeType, String fieldName,
                            Object oldValue, Object newValue, String operator) {
        if (Objects.equals(string(oldValue), string(newValue)) && !"CREATE".equals(changeType) && !"DELETE".equals(changeType)) {
            return;
        }
        AssetChange change = new AssetChange();
        change.setAssetId(assetId);
        change.setAssetName(assetName);
        change.setChangeType(changeType);
        change.setFieldName(fieldName);
        change.setOldValue(limit(string(oldValue), 1000));
        change.setNewValue(limit(string(newValue), 1000));
        change.setOperator(operator);
        assetChanges.save(change);
    }

    public String operator(HttpServletRequest request) {
        String username = attribute(request, "username");
        return username == null ? "system" : username;
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request == null ? null : request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
