package com.cmdb.controller;

import com.cmdb.repo.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
    private final ProjectRepository projects; private final AssetRepository assets; private final UserRepository users;
    public DashboardController(ProjectRepository p,AssetRepository a,UserRepository u){projects=p;assets=a;users=u;}
    @GetMapping public Map<String,Object> stats(){Map<String,Object>m=new LinkedHashMap<>();m.put("projects",projects.count());m.put("assets",assets.count());m.put("online",assets.countByStatus("ONLINE"));m.put("offline",assets.countByStatus("OFFLINE"));m.put("users",users.count());m.put("production",assets.countByEnvironment("PRODUCTION"));return m;}
}
