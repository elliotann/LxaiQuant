package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.entity.SysMenu;

import java.util.List;

public interface IMenuService {

    List<SysMenu> getMenuTree();

    List<SysMenu> getAllMenus();

    void saveMenu(SysMenu menu);

    void updateMenu(SysMenu menu);

    void deleteMenu(Integer id);
}
