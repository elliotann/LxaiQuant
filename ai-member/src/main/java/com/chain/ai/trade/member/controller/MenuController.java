package com.chain.ai.trade.member.controller;

import com.chain.ai.trade.member.entity.SysMenu;
import com.chain.ai.trade.member.service.IMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class MenuController {

    private final IMenuService menuService;

    @GetMapping("/menus")
    public List<SysMenu> getMenus() {
        return menuService.getMenuTree();
    }

    @GetMapping("/menus/all")
    public List<SysMenu> getAllMenus() {
        return menuService.getAllMenus();
    }

    @PostMapping("/menus")
    public void saveMenu(@RequestBody SysMenu menu) {
        menuService.saveMenu(menu);
    }

    @PutMapping("/menus")
    public void updateMenu(@RequestBody SysMenu menu) {
        menuService.updateMenu(menu);
    }

    @DeleteMapping("/menus/{id}")
    public void deleteMenu(@PathVariable Integer id) {
        menuService.deleteMenu(id);
    }
}
