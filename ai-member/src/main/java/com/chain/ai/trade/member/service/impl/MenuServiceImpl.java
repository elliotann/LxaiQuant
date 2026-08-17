package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chain.ai.trade.member.entity.SysMenu;
import com.chain.ai.trade.member.mapper.SysMenuMapper;
import com.chain.ai.trade.member.service.IMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements IMenuService {

    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<SysMenu> getMenuTree() {
        List<SysMenu> all = sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getEnabled, true)
                        .orderByAsc(SysMenu::getSortOrder));

        log.info("getMenuTree: total enabled menus={}", all.size());

        Set<String> userPerms = getCurrentUserPerms();
        log.info("getMenuTree: userPerms={}", userPerms);

        List<SysMenu> filtered = all.stream()
                .filter(m -> m.getPermCode() == null || m.getPermCode().isBlank() || userPerms.contains(m.getPermCode()))
                .toList();

        log.info("getMenuTree: after perm filter={}", filtered.size());

        Map<Integer, List<SysMenu>> parentMap = filtered.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        log.info("getMenuTree: parentMap keys={}", parentMap.keySet());

        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu m : filtered) {
            if (m.getParentId() == null) {
                var children = parentMap.getOrDefault(m.getId(), List.of());
                log.info("getMenuTree: root id={} name={} childrenCount={}", m.getId(), m.getMenuName(), children.size());
                m.setChildren(children);
                roots.add(m);
            }
        }

        log.info("getMenuTree: returning {} roots", roots.size());
        return roots;
    }

    @Override
    public List<SysMenu> getAllMenus() {
        return sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSortOrder));
    }

    @Override
    public void saveMenu(SysMenu menu) {
        sysMenuMapper.insert(menu);
    }

    @Override
    public void updateMenu(SysMenu menu) {
        sysMenuMapper.updateById(menu);
    }

    @Override
    public void deleteMenu(Integer id) {
        sysMenuMapper.deleteById(id);
    }

    private Set<String> getCurrentUserPerms() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }
}
