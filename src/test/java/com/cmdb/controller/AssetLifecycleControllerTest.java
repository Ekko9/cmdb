package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.entity.Asset;
import com.cmdb.repo.AssetLifecycleChangeRepository;
import com.cmdb.repo.AssetRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetLifecycleControllerTest {
    @Test
    void transitionRecordsActorAndUpdatesState() {
        AssetRepository assets = mock(AssetRepository.class);
        AssetLifecycleChangeRepository changes = mock(AssetLifecycleChangeRepository.class);
        AuditService audit = mock(AuditService.class);
        Asset asset = asset("IN_SERVICE");
        when(assets.findById(7L)).thenReturn(Optional.of(asset));
        when(audit.operator(any())).thenReturn("operator");
        when(assets.save(any(Asset.class))).thenAnswer(call -> call.getArgument(0));
        AssetLifecycleController controller = new AssetLifecycleController(assets, changes, audit);
        HttpServletRequest request = mock(HttpServletRequest.class);

        Map<String, Object> result = controller.transition(request, 7L, Map.of("state", "RETIRING", "reason", "replacement"));

        assertEquals("RETIRING", result.get("lifecycleState"));
        assertEquals("RETIRING", asset.getLifecycleState());
        verify(changes).save(argThat(change -> "IN_SERVICE".equals(change.getFromState())
                && "RETIRING".equals(change.getToState()) && "operator".equals(change.getOperator())));
        verify(audit).operation(request, "LIFECYCLE", "ASSET", 7L, "asset-7", "SUCCESS", "IN_SERVICE -> RETIRING");
    }

    @Test
    void rejectsIllegalTransitionWithoutChangingAsset() {
        AssetRepository assets = mock(AssetRepository.class);
        AssetLifecycleChangeRepository changes = mock(AssetLifecycleChangeRepository.class);
        Asset asset = asset("RETIRED");
        when(assets.findById(7L)).thenReturn(Optional.of(asset));
        AssetLifecycleController controller = new AssetLifecycleController(assets, changes, mock(AuditService.class));

        assertThrows(BadRequestException.class, () -> controller.transition(mock(HttpServletRequest.class), 7L, Map.of("state", "IN_SERVICE")));
        assertEquals("RETIRED", asset.getLifecycleState());
        verify(assets, never()).save(any());
        verify(changes, never()).save(any());
    }

    private Asset asset(String state) {
        Asset asset = new Asset(); asset.setId(7L); asset.setName("asset-7"); asset.setLifecycleState(state); return asset;
    }
}
